package dev.obe.disbursement.application.service;

import dev.obe.disbursement.application.port.out.CoreBankingClient;
import dev.obe.disbursement.application.port.out.CoreBankingClient.Step;
import dev.obe.disbursement.application.port.out.CoreOutcomeUnknownException;
import dev.obe.disbursement.application.port.out.CoreRefusedException;
import dev.obe.disbursement.application.port.out.PartnerRules;
import dev.obe.disbursement.domain.model.Disbursement;
import dev.obe.disbursement.domain.model.DisbursementStatus;
import dev.obe.observability.Correlation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Walks each disbursement through its steps. The status says which step is next, so this same code
 * starts a new disbursement and resumes one that a dead worker left halfway. A refusal after earlier
 * steps is undone by compensation. A call with an unknown outcome stops the walk until the core has
 * been asked, because going on could pay twice and undoing could give money away.
 */
@Service
public class DisbursementSaga {

    private static final Logger log = LoggerFactory.getLogger(DisbursementSaga.class);

    private final DisbursementStore store;
    private final CoreBankingClient core;
    private final PartnerRules partnerRules;

    public DisbursementSaga(DisbursementStore store, CoreBankingClient core, PartnerRules partnerRules) {
        this.store = store;
        this.core = core;
        this.partnerRules = partnerRules;
    }

    /** Returns how many disbursements were taken up. */
    public int advanceDue() {
        List<String> numbers = store.claimDue();
        numbers.forEach(this::advance);
        return numbers.size();
    }

    private void advance(String disbursementNo) {
        Disbursement disbursement = store.load(disbursementNo);
        Correlation.run(disbursement.getCorrelationId(), () -> {
            try {
                runSteps(disbursementNo);
            } catch (RuntimeException ex) {
                // The row keeps its last finished step and returns when its lease ends
                log.warn("Disbursement {} stopped: {}", disbursementNo, ex.getMessage());
            }
        });
    }

    private void runSteps(String disbursementNo) {
        Disbursement disbursement = store.load(disbursementNo);
        while (!disbursement.getStatus().isFinal()) {
            runStep(disbursement);
            disbursement = store.load(disbursementNo);
            log.info("Disbursement {} is now {}", disbursementNo, disbursement.getStatus());
            if (disbursement.getStatus() == DisbursementStatus.BELIRSIZ) {
                // Asked again after the lease, which gives a late request time to finish
                return;
            }
        }
    }

    private void runStep(Disbursement disbursement) {
        String number = disbursement.getDisbursementNo();
        switch (disbursement.getStatus()) {
            case ALINDI -> preCheck(disbursement);
            case ONAYLANDI -> reserveLimit(disbursement);
            case LIMIT_AYRILDI -> callCore(number, () -> core.postAccounting(number),
                    DisbursementStatus.MUHASEBELESTI);
            case MUHASEBELESTI -> callCore(number, () -> core.transfer(number), DisbursementStatus.TAMAMLANDI);
            case TELAFI_EDILIYOR -> compensate(disbursement);
            case BELIRSIZ -> resolveUncertain(disbursement);
            default -> throw new IllegalStateException("No step for status " + disbursement.getStatus());
        }
    }

    private void preCheck(Disbursement disbursement) {
        String number = disbursement.getDisbursementNo();
        Optional<BigDecimal> maxAmount = partnerRules.maxAmount(disbursement.getPartnerCode());
        if (maxAmount.isEmpty()) {
            store.fail(number, "Partner is not defined");
        } else if (disbursement.getAmount().compareTo(maxAmount.get()) > 0) {
            store.fail(number, "Amount is above the partner limit");
        } else {
            store.moveTo(number, DisbursementStatus.ONAYLANDI);
        }
    }

    private void reserveLimit(Disbursement disbursement) {
        String number = disbursement.getDisbursementNo();
        try {
            core.reserveLimit(number, disbursement.getCustomerNo(), disbursement.getAmount());
            store.moveTo(number, DisbursementStatus.LIMIT_AYRILDI);
        } catch (CoreRefusedException ex) {
            // Nothing has happened yet, so there is nothing to undo
            store.fail(number, "Credit limit is not enough");
        } catch (CoreOutcomeUnknownException ex) {
            store.markUncertain(number);
        }
    }

    /** A step that can be refused after earlier steps took effect. */
    private void callCore(String number, Runnable call, DisbursementStatus next) {
        try {
            call.run();
            store.moveTo(number, next);
        } catch (CoreRefusedException ex) {
            store.startCompensation(number, "Refused by the core: " + ex.getMessage());
        } catch (CoreOutcomeUnknownException ex) {
            store.markUncertain(number);
        }
    }

    /**
     * Undoes finished steps, newest first. Both calls are safe to repeat, so if this fails halfway the
     * row stays in this status and runs again. The original ledger entry stays, with an opposite one.
     */
    private void compensate(Disbursement disbursement) {
        String number = disbursement.getDisbursementNo();
        if (disbursement.isAccountingPosted()) {
            core.reverseAccounting(number);
        }
        core.releaseLimit(number);
        store.finishCompensation(number);
    }

    /** Throws if the core cannot answer either, leaving the row uncertain. It never guesses. */
    private void resolveUncertain(Disbursement disbursement) {
        String number = disbursement.getDisbursementNo();
        DisbursementStatus running = disbursement.getUncertainFrom();
        if (core.isCompleted(stepOf(running), number)) {
            store.moveTo(number, statusAfter(running));
        } else {
            store.startCompensation(number, "The core did not complete the " + stepOf(running) + " step");
        }
    }

    private Step stepOf(DisbursementStatus running) {
        return switch (running) {
            case ONAYLANDI -> Step.LIMIT;
            case LIMIT_AYRILDI -> Step.ACCOUNTING;
            case MUHASEBELESTI -> Step.TRANSFER;
            default -> throw new IllegalStateException("No core step for status " + running);
        };
    }

    private DisbursementStatus statusAfter(DisbursementStatus running) {
        return switch (running) {
            case ONAYLANDI -> DisbursementStatus.LIMIT_AYRILDI;
            case LIMIT_AYRILDI -> DisbursementStatus.MUHASEBELESTI;
            case MUHASEBELESTI -> DisbursementStatus.TAMAMLANDI;
            default -> throw new IllegalStateException("No step after status " + running);
        };
    }
}

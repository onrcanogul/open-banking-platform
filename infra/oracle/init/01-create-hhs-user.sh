#!/bin/bash
# Runs on first database start. Each service owns a schema so it cannot touch another's tables.
set -euo pipefail

sqlplus -s -L / as sysdba <<EOF
WHENEVER SQLERROR EXIT FAILURE
ALTER SESSION SET CONTAINER = FREEPDB1;
CREATE USER ${ORACLE_HHS_USER} IDENTIFIED BY "${ORACLE_HHS_PASSWORD}" QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE TO ${ORACLE_HHS_USER};
EXIT
EOF

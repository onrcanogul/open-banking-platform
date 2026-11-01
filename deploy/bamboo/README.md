# Bamboo

The server listens on http://localhost:8095, because 8085 on the host belongs to the disbursement service.

```bash
docker compose --profile ci up -d bamboo
```

Once a license key is available:

1. Enter the key in the setup wizard and create the admin user.
2. Add a linked Git repository named `obe` with the URL `file:///repo.git`.
3. Create the project with the key `OBE` and enable Bamboo Specs on the linked repository.
4. Start the agent and approve it on the server:

```bash
docker compose --profile ci up -d bamboo-agent
```

The agent shares the host's Docker engine, so a build can start any container on this machine.

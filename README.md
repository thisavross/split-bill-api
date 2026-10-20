# Split Bill API

Spring Boot REST API for sharing expenses in a group and working out who owes whom.

- Java 17, Spring Boot 3.3, Maven
- All money is `BigDecimal` with scale 2 (no `float`/`double` anywhere)
- Multiple split strategies, minimised settlement, payment recording, categories, audit timestamps

## Personalization

| Item | Value |
|------|-------|
| GitHub username | `thisavross` |
| Unicode sum | `1110` |
| `service_charge_pct` | `0` (1110 % 10 = 0, i.e. 0%) |

The percentage is **computed in code** (`ServiceChargeCalculator`) from the `splitbill.github-username`
property. Set it either by editing `src/main/resources/application.properties` or with the
`GITHUB_USERNAME` environment variable. `service_charge_amount` = `service_charge_pct`% of total group expenses.
It is reported separately in the settlement response and is not added to individual debts.

## Build & run

```bash
# tests
mvn test

# run locally (http://localhost:8080)
GITHUB_USERNAME=thisavross mvn spring-boot:run

# or build the jar
mvn clean package
GITHUB_USERNAME=thisavross java -jar target/split-bill-api-1.0.0.jar

# docker (multi-stage build)
docker build -t split-bill-api .
docker run --rm -p 8080:8080 -e GITHUB_USERNAME=thisavross split-bill-api
```

## Endpoints

JSON is `snake_case`. Participants are identified by name (unique per group, case-insensitive).

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/groups` | Create group |
| GET | `/api/v1/groups/{id}` | Get group |
| POST | `/api/v1/groups/{id}/expenses` | Add expense |
| GET | `/api/v1/groups/{id}/expenses` | List expenses (audit trail via `created_at`) |
| POST | `/api/v1/groups/{id}/payments` | Record a settlement payment |
| GET | `/api/v1/groups/{id}/settlement` | Settlement summary |
| GET | `/api/v1/groups/{id}/categories` | Per-category totals |

### Example curl commands

```bash
# 1. Create a group
curl -s -X POST localhost:8080/api/v1/groups -H 'Content-Type: application/json' \
  -d '{"name":"Bali Trip","participants":["asep","beni","siti"]}'
# -> copy the "id" into GID
GID=<group-id>

# 2. Equal split (default). Omit "splits" to split among everyone.
curl -s -X POST localhost:8080/api/v1/groups/$GID/expenses -H 'Content-Type: application/json' \
  -d '{"description":"Dinner","amount":90.00,"paid_by":"asep","category":"food"}'

# 3. Equal split among a subset
curl -s -X POST localhost:8080/api/v1/groups/$GID/expenses -H 'Content-Type: application/json' \
  -d '{"description":"Taxi","amount":30.00,"paid_by":"beni","category":"transport",
       "splits":[{"participant":"asep"},{"participant":"beni"}]}'

# 4. Percentage split (must sum to 100)
curl -s -X POST localhost:8080/api/v1/groups/$GID/expenses -H 'Content-Type: application/json' \
  -d '{"description":"Hotel","amount":200.00,"paid_by":"siti","category":"accommodation","split_type":"PERCENTAGE",
       "splits":[{"participant":"asep","value":50},{"participant":"beni","value":30},{"participant":"siti","value":20}]}'

# 5. Exact split (must sum to the amount)
curl -s -X POST localhost:8080/api/v1/groups/$GID/expenses -H 'Content-Type: application/json' \
  -d '{"description":"Snacks","amount":25.50,"paid_by":"beni","split_type":"EXACT",
       "splits":[{"participant":"asep","value":10.50},{"participant":"siti","value":15.00}]}'

# 6. List expenses
curl -s localhost:8080/api/v1/groups/$GID/expenses

# 7. Settlement summary (includes service_charge_pct / service_charge_amount)
curl -s localhost:8080/api/v1/groups/$GID/settlement

# 8. Record that siti paid asep 30.00
curl -s -X POST localhost:8080/api/v1/groups/$GID/payments -H 'Content-Type: application/json' \
  -d '{"from":"siti","to":"asep","amount":30.00}'

# 9. Category summary
curl -s localhost:8080/api/v1/groups/$GID/categories
```

Sample settlement response (after the first two expenses):

```json
{
  "group_id": "…",
  "group_name": "Bali Trip",
  "total_expenses": 120.00,
  "service_charge_pct": 0,
  "service_charge_amount": 0.00,
  "fully_settled": false,
  "balances": [
    {"participant": "asep", "total_paid": 90.00, "total_owed": 45.00, "net_balance": 45.00},
    {"participant": "beni",   "total_paid": 30.00, "total_owed": 45.00, "net_balance": -15.00},
    {"participant": "siti", "total_paid": 0.00,  "total_owed": 30.00, "net_balance": -30.00}
  ],
  "transfers": [
    {"from": "siti", "to": "asep", "amount": 30.00},
    {"from": "beni",   "to": "asep", "amount": 15.00}
  ]
}
```

Errors use a consistent shape: `{timestamp, status, error, message, details}` (400 validation, 404 unknown group).

## Design notes

- **Money:** `BigDecimal`, scale 2. Input with more than 2 decimals is rejected rather than silently rounded.
- **No lost cents:** equal splits hand leftover cents to the first participants; percentage splits push rounding drift to the last
  participant. Every expense's shares sum exactly to its amount, so group balances always sum to zero.
- **Settlement:** net balance per person (paid − owed ± recorded payments), then a greedy largest-debtor/largest-creditor match,
  which yields at most n−1 transfers.
- **Payments:** validated against outstanding balances (cannot overpay, cannot pay someone who is not owed).
- **Structure:** controller → service → in-memory repository; split and settlement maths are pure classes with no Spring dependency.
- **Storage:** in-memory behind a `GroupRepository` interface, so data is lost on restart; swapping in JPA/Postgres only needs a new implementation.

## Submission question

> What was the hardest design decision you made while building this, and what trade-off did you accept?

The hardest design decision was choosing how to calculate settlements efficiently. I used a greedy algorithm that matches the largest debtor with the largest creditor, which keeps the implementation deterministic and guarantees at most n−1 transfers, although it does not always produce the absolute minimum number of transfers. I accepted this trade-off because the algorithm is efficient, easy to understand, and straightforward to test. I also made sure that monetary calculations use `BigDecimal` and that rounding differences are handled explicitly so the total split amounts remain consistent.

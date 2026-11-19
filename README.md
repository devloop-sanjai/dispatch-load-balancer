## Testing with Postman
Import `postman/dispatch-load-balancer.postman_collection.json` into Postman and run it with the Collection Runner. The API is tested through Postman; Swagger UI is provided only as API documentation.

## Design & Code Structure
- `controller/`: REST endpoints only (HTTP in, HTTP out)
- `service/`: orchestration, validation of business rules, DTO mapping
- `optimizer/`: framework-free allocation logic and Haversine distance
- `repository/` and `entity/`: persistence with upsert (single batched lookup, then `saveAll`)
- `dto/`: request and response models with Bean Validation
- `exception/`: global error handling with consistent error responses

### Design patterns
- **Strategy:** `DispatchOptimizer` and `DistanceCalculator` interfaces; the greedy algorithm and Haversine are interchangeable implementations.
- **Dependency Injection:** components are wired in `AppConfig`.
- **DTO:** API models are separate from database entities.
- **Repository:** data access through Spring Data.

### Tests
- `HaversineDistanceCalculatorTest` and `GreedyDispatchOptimizerTest`: unit tests for distance and allocation rules.
- `DispatchApiIntegrationTest`: API tests for validation, errors and the full flow.

# When to Mock

Mock at system boundaries only:

- External APIs and SDKs
- Databases when a real test database is not practical
- Time and randomness
- File system access
- Queues, email, and other infrastructure clients

Don't mock:

- Your own classes or modules
- Internal collaborators
- Private methods
- Anything you control directly

## Designing for Mockability

At boundaries, make dependencies easy to replace:

- Inject clients or ports instead of constructing them internally.
- Keep interfaces small and specific.
- Prefer fakes or in-memory adapters when they are simpler than mocks.

Language cues:

- Java: inject interfaces or clients; use Mockito at boundaries only.
- Go: define small interfaces on the consumer side; prefer hand-written fakes and `httptest` before third-party mocking.
- Python: pass collaborators in, or monkeypatch imported boundary objects; use `unittest.mock` or `pytest.monkeypatch` for external boundaries.

Examples:

```java
class PaymentService {
  private final PaymentClient client;

  PaymentService(PaymentClient client) {
    this.client = client;
  }

  Receipt process(Order order) {
    return client.charge(order.total());
  }
}
```

```go
type PaymentClient interface {
  Charge(total Money) Receipt
}

func ProcessPayment(order Order, client PaymentClient) Receipt {
  return client.Charge(order.Total)
}
```

```python
def process_payment(order, payment_client):
    return payment_client.charge(order.total)
```

# Good and Bad Tests

## Good tests

Test through public interfaces with the project's native test style.

Characteristics:

- Verify behavior callers care about
- Use public APIs only
- Survive internal refactors
- Describe WHAT, not HOW
- Keep each test focused on one behavior

Examples:

```java
@Test
void userCanCheckoutWithValidCart() {
  Cart cart = createCart();
  cart.add(product);

  Receipt receipt = checkout(cart, paymentMethod);
  assertEquals("confirmed", receipt.status());
}
```

```go
func TestCheckout(t *testing.T) {
  cart := NewCart()
  cart.Add(product)

  got, err := Checkout(cart, paymentMethod)
  if err != nil {
    t.Fatal(err)
  }
  if got.Status != "confirmed" {
    t.Fatalf("status = %q, want confirmed", got.Status)
  }
}
```

```python
def test_user_can_checkout_with_valid_cart():
    cart = create_cart()
    cart.add(product)

    receipt = checkout(cart, payment_method)
    assert receipt.status == "confirmed"
```

## Bad tests

Implementation-detail tests are coupled to internal structure.

Red flags:

- Mocking internal collaborators
- Testing private methods
- Asserting on call counts or call order
- Test breaks when refactoring without behavior change
- Test name describes HOW, not WHAT
- Verifying through external side effects instead of the interface

```python
def test_create_user_saves_to_database():
    create_user({"name": "Alice"})
    row = db.query("SELECT * FROM users WHERE name = ?", ["Alice"])
    assert row is not None
```

Good:

```python
def test_create_user_makes_user_retrievable():
    user = create_user({"name": "Alice"})
    retrieved = get_user(user.id)
    assert retrieved.name == "Alice"
```

Tautological tests restate the implementation, so they pass by construction.

```go
func TestCalculateTotal(t *testing.T) {
  items := []Item{{Price: 10}, {Price: 5}}
  expected := sumPrices(items) // same logic as production code

  if got := CalculateTotal(items); got != expected {
    t.Fatalf("got %d, want %d", got, expected)
  }
}
```

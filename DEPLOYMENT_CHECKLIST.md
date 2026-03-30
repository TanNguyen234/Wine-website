# ✅ CHECKLIST - TRIỂN KHAI PAYMENT FIX

## 📋 PRE-DEPLOYMENT

### 1. Code Changes
- [x] PaymentService.java - Refactor transaction management
- [x] application.properties - Environment variables
- [x] pom.xml - Add spring-dotenv dependency
- [x] .gitignore - Add .env files
- [x] DotEnvLoader.java - Created
- [x] spring.factories - Created

### 2. Configuration Files
- [x] .env - Created with your Stripe keys
- [x] .env.example - Template created
- [x] docs/ - Documentation created

### 3. Build
- [x] Code compiles without errors
- [x] Maven dependencies resolved

---

## 🚀 DEPLOYMENT STEPS

### Developer Actions

#### 1. Verify File Structure
```bash
# Check files exist
- [ ] .env (exists and contains keys)
- [ ] .env.example (template)
- [ ] src/main/java/com/strongwine/strongwine/config/DotEnvLoader.java
- [ ] src/main/resources/META-INF/spring.factories
```

#### 2. Clean Build
```bash
- [ ] Run: mvn clean install
- [ ] Check: No compilation errors
- [ ] Check: Dependencies downloaded
```

#### 3. Restart Application
```bash
- [ ] Run: mvn spring-boot:run
- [ ] Check console: "✅ Loaded 2 variables from .env file"
- [ ] Check console: "Stripe API initialized"
- [ ] Check console: No errors during startup
```

#### 4. Test Payment Flow
```bash
- [ ] Login as user (user1/password)
- [ ] Add wine to cart
- [ ] Proceed to checkout
- [ ] Fill in shipping information
- [ ] Click "Thanh toán" button
- [ ] Expected: Redirect to Stripe Checkout
- [ ] Expected: NO "Could not commit JPA transaction" error
```

#### 5. Test with Stripe Test Card
```bash
- [ ] Card: 4242 4242 4242 4242
- [ ] Expiry: Any future date
- [ ] CVC: Any 3 digits
- [ ] Click "Pay"
- [ ] Expected: Payment success
- [ ] Expected: Redirect back to success page
```

#### 6. Verify Database
```sql
-- Check Payment record
- [ ] SELECT * FROM payments WHERE order_id = ?
- [ ] Expected: Payment record exists
- [ ] Expected: gateway_session_id populated
- [ ] Expected: status = 'PENDING' or 'SUCCESS'

-- Check Transaction logs
- [ ] SELECT * FROM payment_transactions WHERE payment_id = ?
- [ ] Expected: Multiple transaction records
- [ ] Expected: SESSION_CREATED, STRIPE_SESSION, etc.
```

---

## 🧪 TESTING CHECKLIST

### Scenario 1: Happy Path ✅
- [ ] Add product to cart
- [ ] Checkout
- [ ] Payment succeeds
- [ ] Order status = PAID
- [ ] Payment status = SUCCESS

### Scenario 2: Stripe API Failure
- [ ] Simulate network issue (optional)
- [ ] Check: Payment record still exists
- [ ] Check: Payment status = FAILED
- [ ] Check: User can retry

### Scenario 3: User Cancels Payment
- [ ] Start checkout
- [ ] Go to Stripe
- [ ] Click "Back" button
- [ ] Check: Cancel page shown
- [ ] Check: Order status = PENDING or CANCELLED

---

## 🔒 SECURITY CHECKLIST

### Environment Variables
- [x] .env file created
- [x] .env added to .gitignore
- [ ] Verify: .env NOT in Git staging area
  ```bash
  git status  # Should NOT see .env
  ```

### Stripe Keys
- [ ] STRIPE_SECRET_KEY set in .env
- [ ] STRIPE_PUBLIC_KEY set in .env
- [ ] Keys are Test mode (sk_test_, pk_test_)
- [ ] For production: Use Live keys (sk_live_, pk_live_)

### Git Safety
- [ ] Run: git status
- [ ] Verify: .env is NOT listed
- [ ] Verify: .env.example IS listed (template only)

---

## 📊 MONITORING

### Application Logs
```bash
# Check on startup
- [ ] "✅ Loaded 2 variables from .env file"
- [ ] "Stripe API initialized"

# Check during payment
- [ ] Payment creation logs
- [ ] Stripe session creation logs
- [ ] No transaction errors
```

### Database Monitoring
```sql
-- Monitor payments
- [ ] SELECT COUNT(*) FROM payments;
- [ ] SELECT status, COUNT(*) FROM payments GROUP BY status;

-- Monitor failed payments
- [ ] SELECT * FROM payments WHERE status = 'FAILED';
- [ ] Check gateway_response for error details
```

---

## 🎯 SUCCESS CRITERIA

### Must Have ✅
- [x] Code compiles
- [ ] Application starts without errors
- [ ] .env file loads correctly
- [ ] Stripe initializes
- [ ] Payment creates without transaction error
- [ ] User can checkout successfully

### Nice to Have 🌟
- [ ] Webhook configured (optional for dev)
- [ ] Full payment flow tested
- [ ] Error scenarios tested
- [ ] Admin can view payment logs

---

## ❌ ROLLBACK PLAN

If something goes wrong:

1. **Restore Previous Version**
   ```bash
   git checkout HEAD~1 -- src/main/java/com/strongwine/strongwine/service/PaymentService.java
   git checkout HEAD~1 -- src/main/resources/application.properties
   git checkout HEAD~1 -- pom.xml
   ```

2. **Clean Build**
   ```bash
   mvn clean install
   ```

3. **Restart**
   ```bash
   mvn spring-boot:run
   ```

---

## 📞 HELP & SUPPORT

If issues occur, check:

1. **Console logs** - Look for error messages
2. **Database** - Check payment and transaction records
3. **Documentation**:
   - `QUICK_START.md` - Quick guide
   - `docs/FIX_PAYMENT_ERROR.md` - Troubleshooting
   - `docs/PAYMENT_FLOW_DIAGRAM.md` - Technical details

---

## ✅ SIGN-OFF

### Developer
- [ ] All code changes reviewed
- [ ] All tests passed
- [ ] Documentation complete
- [ ] Ready for deployment

**Signature**: _________________  
**Date**: _________________

### QA/Tester
- [ ] Payment flow tested
- [ ] Error scenarios tested
- [ ] Security verified
- [ ] Approved for production

**Signature**: _________________  
**Date**: _________________

---

## 🎉 COMPLETION

When all checkboxes are marked:

**✅ PAYMENT FIX DEPLOYMENT COMPLETE**

Application is ready for production use!

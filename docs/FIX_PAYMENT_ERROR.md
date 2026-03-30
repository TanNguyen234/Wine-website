# ✅ HƯỚNG DẪN FIX LỖI THANH TOÁN

## 🔴 Các lỗi đã sửa

### 1. **"Could not commit JPA transaction"**

**❌ Nguyên nhân**: 
- Stripe API call được thực hiện **TRONG** transaction JPA
- Khi Stripe timeout/fail → exception → transaction rollback conflict

**✅ Giải pháp**:
Tách flow thành 3 bước độc lập:
```
Step 1: Create Payment record (Transaction #1)
Step 2: Call Stripe API (NO transaction)
Step 3: Update Payment with session (Transaction #2)
```

**Code changes**:
- `PaymentService.createPaymentSession()` → No @Transactional
- `PaymentService.createInitialPayment()` → @Transactional(REQUIRES_NEW)
- `PaymentService.updatePaymentWithSession()` → @Transactional(REQUIRES_NEW)

---

### 2. **Stripe Keys hardcoded trong application.properties**

**❌ Risk**: 
- Secret keys committed vào Git
- Dễ bị lộ thông tin nhạy cảm

**✅ Giải pháp**:
- Tạo file `.env` (được gitignore)
- Load environment variables qua `DotEnvLoader`
- Fallback về default values nếu không có .env

---

## 📋 HƯỚNG DẪN SETUP

### Bước 1: Tạo file `.env`

```bash
# Từ thư mục gốc dự án
copy .env.example .env
```

### Bước 2: Điền Stripe Keys vào `.env`

```env
STRIPE_SECRET_KEY=sk_test_51TEXgOH9vWoAs7j9...
STRIPE_PUBLIC_KEY=pk_test_51TEXgOH9vWoAs7j9...
STRIPE_WEBHOOK_SECRET=whsec_...  # Optional cho dev
```

### Bước 3: Build lại project

```bash
# Maven
mvn clean install

# Hoặc trong VS Code
# Ctrl+Shift+P → "Java: Clean Java Language Server Workspace"
# Sau đó rebuild
```

### Bước 4: Restart ứng dụng

```bash
mvn spring-boot:run
```

---

## 🧪 TEST

### Test thanh toán:

1. **Thêm sản phẩm vào giỏ**
2. **Checkout** → điền thông tin
3. **Bấm "Thanh toán"**

**Kết quả mong đợi**:
- ✅ Redirect sang Stripe Checkout
- ❌ **KHÔNG** còn lỗi "Could not commit JPA transaction"

### Test Stripe:

**Test Card Numbers**:
- Success: `4242 4242 4242 4242`
- Decline: `4000 0000 0000 0002`

**Expiry**: Bất kỳ tháng/năm tương lai  
**CVC**: Bất kỳ 3 chữ số  
**ZIP**: Bất kỳ

---

## 🔍 KIỂM TRA LOG

Khi khởi động, bạn sẽ thấy:

```
✅ Loaded 3 variables from .env file
Stripe API initialized
```

Nếu không có file `.env`:
```
⚠️  No .env file found. Using default configuration.
```

---

## ❌ LỖI THƯỜNG GẶP

### "Stripe secret key is missing"
→ Kiểm tra file `.env` có đúng tên và vị trí
→ Restart ứng dụng

### Vẫn còn lỗi transaction
→ Clear cache: `mvn clean`
→ Rebuild: `mvn install`
→ Restart Spring Boot

### Payment tạo nhưng không redirect
→ Check console log xem Stripe API error
→ Verify Stripe key đã đúng chưa

---

## 📞 Tài liệu tham khảo

- [Stripe Testing Cards](https://stripe.com/docs/testing#cards)
- [Stripe Checkout](https://stripe.com/docs/payments/checkout)
- [Spring Transaction Management](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction)

---

## 🔐 BẢO MẬT

**⚠️ LƯU Ý**:
- **KHÔNG** commit file `.env` lên Git
- **KHÔNG** share Stripe secret key
- Production: Sử dụng Live keys (`sk_live_`, `pk_live_`)
- Webhook: Bắt buộc phải có `STRIPE_WEBHOOK_SECRET` trong production

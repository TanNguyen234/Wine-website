# 🚀 QUICK START - Fix Lỗi Thanh Toán

## ⚡ 3 BƯỚC FIX NHANH

### 1️⃣ Tạo file `.env`
```bash
copy .env.example .env
```

### 2️⃣ Điền Stripe Keys vào `.env`
```env
STRIPE_SECRET_KEY=sk_test_51TEXgOH9vWoAs7j9qBrtuKLUovD3GnAXXKdeNwHJlhUS4CyJgYDT4AbYmmb5A7FxNDPW1q4IYGWwshO8gVgJdcU500arkIcmou
STRIPE_PUBLIC_KEY=pk_test_51TEXgOH9vWoAs7j9B3zkj9Cluef9p9OifA9demKqIhAQAETGxG83Vx11LOHJDlUcZzrADaFCP3ZQXsrXTdrMmw5O004Mo7fTyz
STRIPE_WEBHOOK_SECRET=
```

### 3️⃣ Restart ứng dụng
```bash
mvn clean spring-boot:run
```

---

## ✅ TEST

1. Thêm sản phẩm vào giỏ
2. Checkout → điền thông tin
3. Bấm "Thanh toán"

**Test Card**: `4242 4242 4242 4242`

---

## 🎯 KẾT QUẢ

- ✅ **KHÔNG** còn lỗi "Could not commit JPA transaction"
- ✅ Redirect sang Stripe Checkout thành công
- ✅ Payment được lưu vào DB

---

## ❌ NẾU VẪN LỖI

```bash
# Clear cache và rebuild
mvn clean install

# Restart lại
mvn spring-boot:run
```

---

## 📖 CHI TIẾT

Xem thêm:
- `docs/FIX_PAYMENT_ERROR.md` - Hướng dẫn đầy đủ
- `docs/STRIPE_SETUP.md` - Setup Stripe chi tiết
- `docs/FIX_PAYMENT_TRANSACTION_SUMMARY.md` - Phân tích kỹ thuật

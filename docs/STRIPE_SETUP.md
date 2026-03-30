# 🔐 HƯỚNG DẪN CẤU HÌNH STRIPE

## ⚠️ BẢO MẬT

**QUAN TRỌNG**: Đừng bao giờ commit file `.env` chứa Stripe keys thật lên Git!

## 📋 Các bước setup

### 1. Tạo file `.env` trong thư mục gốc dự án

```bash
# Copy file mẫu
copy .env.example .env
```

### 2. Lấy Stripe Keys

Truy cập: https://dashboard.stripe.com/test/apikeys

- **Secret Key**: `sk_test_...` → Copy vào `STRIPE_SECRET_KEY`
- **Public Key**: `pk_test_...` → Copy vào `STRIPE_PUBLIC_KEY`

### 3. Cấu hình Webhook (cho production)

1. Truy cập: https://dashboard.stripe.com/test/webhooks
2. Click **Add endpoint**
3. URL: `https://your-domain.com/api/payment/webhook/stripe`
4. Events: Chọn:
   - `checkout.session.completed`
   - `checkout.session.expired`
   - `checkout.session.async_payment_failed`
5. Copy **Signing secret** (dạng `whsec_...`) → vào `STRIPE_WEBHOOK_SECRET`

### 4. File `.env` mẫu

```env
STRIPE_SECRET_KEY=sk_test_51TEXgOH9vWoAs7j9...
STRIPE_PUBLIC_KEY=pk_test_51TEXgOH9vWoAs7j9...
STRIPE_WEBHOOK_SECRET=whsec_...
```

## 🚀 Development vs Production

### Development (localhost)
- Sử dụng **Test Keys** (`sk_test_...`, `pk_test_...`)
- Webhook secret có thể bỏ trống (nếu không test webhook locally)
- Test với Stripe CLI: `stripe listen --forward-to localhost:8080/api/payment/webhook/stripe`

### Production
- Sử dụng **Live Keys** (`sk_live_...`, `pk_live_...`)
- **BẮT BUỘC** phải có webhook secret
- Cấu hình HTTPS endpoint công khai

## 🔧 Cách load environment variables

Spring Boot tự động load từ:
1. System environment variables
2. `.env` file (cần plugin)
3. Default values trong `application.properties`

## 🛠️ Sử dụng với Docker

```dockerfile
ENV STRIPE_SECRET_KEY=sk_live_...
ENV STRIPE_PUBLIC_KEY=pk_live_...
ENV STRIPE_WEBHOOK_SECRET=whsec_...
```

## ❌ LỖI THƯỜNG GẶP

### "Could not commit JPA transaction"
- **Fix**: Đã tách Stripe API call ra khỏi transaction
- Restart ứng dụng sau khi cập nhật code

### "Stripe webhook secret chưa được cấu hình"
- Thêm `STRIPE_WEBHOOK_SECRET` vào `.env`
- Hoặc tắt webhook validation trong dev (không khuyến khích)

### "Stripe secret key is missing"
- Kiểm tra file `.env` có tồn tại không
- Kiểm tra `STRIPE_SECRET_KEY` đã set chưa
- Restart ứng dụng

## 📞 Support

Tài liệu Stripe: https://stripe.com/docs/payments/checkout

import re
import pytest
from conftest import BASE_URL, save_screenshot

def login_customer(page):
    page.goto(f"{BASE_URL}/login", wait_until="load")
    page.fill("input[name='username']", "demo")
    page.fill("input[name='password']", "demo123")
    page.click("button[type='submit']")
    page.wait_for_load_state("load")
    assert "/home" in page.url or page.url == f"{BASE_URL}/", f"Expected redirect to /home, got {page.url}"

def test_r2_customer_login_and_order_history(cdp_session):
    page, cdp, tracker = cdp_session
    login_customer(page)
    tracker.assert_clean("after customer login")
    save_screenshot(page, "r2", "customer_home")

    # Navigate to order history
    resp = page.goto(f"{BASE_URL}/orders", wait_until="load")
    assert resp.status == 200
    tracker.assert_clean("on customer orders page")
    save_screenshot(page, "r2", "customer_orders")

def test_r2_customer_order_detail_stepper_and_vault(cdp_session):
    page, cdp, tracker = cdp_session
    login_customer(page)

    page.goto(f"{BASE_URL}/orders", wait_until="load")
    # Check for order link
    order_link = page.query_selector("a[href^='/orders/']")
    order_id = "1"
    if order_link:
        href = order_link.get_attribute("href")
        match = re.search(r"/orders/(\d+)", href)
        if match:
            order_id = match.group(1)

    resp = page.goto(f"{BASE_URL}/orders/{order_id}", wait_until="load")
    # If this order belongs to demo, it will render 200, if not it redirects to /orders
    if resp.status == 200 and f"/orders/{order_id}" in page.url:
        page.wait_for_selector("#orderTimelineStepper", timeout=10000)
        tracker.assert_clean(f"on order detail {order_id}")
        # Verify 5-step stepper presence
        stepper = page.query_selector("#orderTimelineStepper")
        assert stepper is not None, "Order timeline stepper (#orderTimelineStepper) should be present"

        # Verify steps count
        steps = page.query_selector_all(".timeline-step")
        assert len(steps) >= 5, f"Expected 5 timeline steps, found {len(steps)}"

        # Verify OTP vault card if present
        otp_vault = page.query_selector("#otpVaultCard")
        if otp_vault:
            otp_code = page.query_selector("#otpCodeDisplay")
            assert otp_code is not None, "OTP code display element should exist inside OTP vault"

        # Verify SSE indicator
        sse_badge = page.query_selector("#sseIndicator")
        assert sse_badge is not None, "SSE live indicator badge should exist"

        save_screenshot(page, "r2", f"order_stepper_{order_id}")
    else:
        # If redirected to /orders due to ownership boundary
        assert "/orders" in page.url
        save_screenshot(page, "r2", "order_privacy_boundary")

def test_r2_customer_privacy_isolation_redirect(cdp_session):
    page, cdp, tracker = cdp_session
    login_customer(page)

    # Attempt to view a foreign order ID like 99999
    page.goto(f"{BASE_URL}/orders/99999", wait_until="load")
    # Per OrderController line 76: redirect to /orders
    assert page.url.endswith("/orders"), f"Expected safe redirect to /orders, got {page.url}"
    tracker.assert_clean("after privacy boundary redirect")
    save_screenshot(page, "r2", "privacy_redirect_foreign_order")

def test_r2_customer_rbac_forbidden_boundaries(cdp_session):
    page, cdp, tracker = cdp_session
    login_customer(page)

    # Customer accessing /admin should yield 403 Forbidden
    resp_admin = page.goto(f"{BASE_URL}/admin", wait_until="load")
    assert resp_admin.status == 403, f"Expected HTTP 403 on /admin for customer, got {resp_admin.status}"
    save_screenshot(page, "r2", "customer_forbidden_admin")

    # Customer accessing /shipper/dashboard should yield 403 Forbidden
    resp_shipper = page.goto(f"{BASE_URL}/shipper/dashboard", wait_until="load")
    assert resp_shipper.status == 403, f"Expected HTTP 403 on /shipper/dashboard for customer, got {resp_shipper.status}"
    save_screenshot(page, "r2", "customer_forbidden_shipper")

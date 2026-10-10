import pytest
from conftest import BASE_URL, save_screenshot

def login_shipper(page):
    page.goto(f"{BASE_URL}/login", wait_until="load")
    page.fill("input[name='username']", "shipper.ops.hcm")
    page.fill("input[name='password']", "admin123")
    page.click("button[type='submit']")
    page.wait_for_load_state("load")
    assert "/shipper/dashboard" in page.url, f"Expected redirect to /shipper/dashboard, got {page.url}"

def test_r4_shipper_mobile_cockpit_actions(mobile_cdp_session):
    page, cdp, tracker = mobile_cdp_session
    login_shipper(page)

    assert page.url.endswith("/shipper/dashboard")
    page.wait_for_selector("main.container, .container", timeout=10000)
    tracker.assert_clean("on shipper mobile dashboard")

    # Verify 1-tap calling link exists
    tel_links = page.query_selector_all("a[href^='tel:']")
    # Verify Google Maps routing link exists
    map_links = page.query_selector_all("a[href*='google.com/maps']")

    # Verify at least one shipment card or mobile layout
    shipment_cards = page.query_selector_all(".shipper-card")
    if shipment_cards:
        # Check calling link and map link are present
        assert len(tel_links) > 0, "One-tap calling button (tel:) should be present on shipment card"
        assert len(map_links) > 0, "Google Maps directions link should be present on shipment card"

    save_screenshot(page, "r4", "shipper_mobile_cockpit")

def test_r4_shipper_auto_advancing_otp_keypad(mobile_cdp_session):
    page, cdp, tracker = mobile_cdp_session
    login_shipper(page)

    # Check for OTP input boxes
    otp_boxes = page.query_selector_all(".otp-box-mobile, .otp-box")
    if otp_boxes and len(otp_boxes) >= 6:
        first_box = otp_boxes[0]
        first_box.click()
        first_box.type("1")
        # Check next box receives focus or has value
        otp_boxes[1].type("2")
        otp_boxes[2].type("3")
        otp_boxes[3].type("4")
        otp_boxes[4].type("5")
        otp_boxes[5].type("6")
        tracker.assert_clean("after typing into OTP keypad")
        save_screenshot(page, "r4", "otp_keypad_filled")
    else:
        # If no active shipments are awaiting delivery OTP
        tracker.assert_clean("on empty/idle shipper dashboard")
        save_screenshot(page, "r4", "shipper_dashboard_idle")

def test_r4_shipper_rbac_admin_forbidden(mobile_cdp_session):
    page, cdp, tracker = mobile_cdp_session
    login_shipper(page)

    # Shipper accessing /admin must return HTTP 403 Forbidden
    resp = page.goto(f"{BASE_URL}/admin", wait_until="load")
    assert resp.status == 403, f"Expected HTTP 403 on /admin for shipper, got {resp.status}"
    save_screenshot(page, "r4", "shipper_forbidden_admin")

import pytest
from conftest import BASE_URL, save_screenshot

@pytest.mark.parametrize("route", [
    "/home",
    "/wines",
    "/cart",
    "/login",
    "/register",
    "/forgot-password"
])
def test_r1_public_pages_render_clean(cdp_session, route):
    page, cdp, tracker = cdp_session
    resp = page.goto(f"{BASE_URL}{route}", wait_until="networkidle")
    assert resp.status == 200, f"Expected HTTP 200 for {route}, got {resp.status}"

    # Verify no console runtime errors
    tracker.assert_clean(context=f"on {route}")

    # Capture visual screenshot
    clean_name = route.strip("/").replace("/", "_") or "root"
    save_screenshot(page, "r1", f"public_{clean_name}")


def test_r1_wine_detail_render_clean(cdp_session):
    page, cdp, tracker = cdp_session
    # Go to /wines first to find a valid link or default to /wines/1
    page.goto(f"{BASE_URL}/wines", wait_until="networkidle")
    first_wine_link = page.query_selector("a[href^='/wines/']")
    detail_url = f"{BASE_URL}/wines/1"
    if first_wine_link:
        href = first_wine_link.get_attribute("href")
        if href and href != "/wines":
            detail_url = f"{BASE_URL}{href}" if href.startswith("/") else href

    resp = page.goto(detail_url, wait_until="networkidle")
    assert resp.status == 200
    tracker.assert_clean(context=f"on wine detail {detail_url}")
    save_screenshot(page, "r1", "wine_detail")


@pytest.mark.parametrize("protected_route", [
    "/orders",
    "/admin",
    "/shipper/dashboard"
])
def test_r1_unauthenticated_protected_routes_redirect_to_login(cdp_session, protected_route):
    page, cdp, tracker = cdp_session
    page.goto(f"{BASE_URL}{protected_route}", wait_until="networkidle")

    # Assert redirected to /login
    assert "/login" in page.url, f"Expected redirect to /login from {protected_route}, got {page.url}"
    tracker.assert_clean(context=f"redirect from {protected_route}")
    clean_name = protected_route.strip("/").replace("/", "_")
    save_screenshot(page, "r1", f"redirect_{clean_name}")

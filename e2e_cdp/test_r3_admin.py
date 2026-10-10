import pytest
from conftest import BASE_URL, save_screenshot
from utils.excel_validator import validate_excel_binary

def login_admin(page):
    page.goto(f"{BASE_URL}/login", wait_until="load")
    page.fill("input[name='username']", "admin")
    page.fill("input[name='password']", "admin123")
    page.click("button[type='submit']")
    page.wait_for_load_state("load")
    assert "/admin" in page.url, f"Expected redirect to /admin, got {page.url}"

def test_r3_admin_dashboard_kpis_and_charts(cdp_session):
    page, cdp, tracker = cdp_session
    login_admin(page)

    # Verify admin dashboard loaded with 200
    assert page.url.endswith("/admin")
    page.wait_for_selector("#revenueChart", timeout=10000)
    tracker.assert_clean("on admin dashboard")

    # Verify Chart.js canvas elements
    revenue_chart = page.query_selector("#revenueChart")
    category_chart = page.query_selector("#categoryPieChart")
    assert revenue_chart is not None, "Revenue chart canvas (#revenueChart) must exist"
    assert category_chart is not None, "Category doughnut chart canvas (#categoryPieChart) must exist"

    # Verify Live Cockpit badge
    live_badge = page.query_selector("#adminLiveBadge")
    assert live_badge is not None, "Admin live cockpit badge (#adminLiveBadge) must exist"

    save_screenshot(page, "r3", "admin_dashboard_charts")

def test_r3_admin_excel_import_dropzone(cdp_session):
    page, cdp, tracker = cdp_session
    login_admin(page)

    resp = page.goto(f"{BASE_URL}/admin/wines/import", wait_until="load")
    assert resp.status == 200
    page.wait_for_selector("#excelDropZone", timeout=10000)
    tracker.assert_clean("on admin wines import page")

    # Verify Excel Dropzone
    dropzone = page.query_selector("#excelDropZone")
    assert dropzone is not None, "Excel drop zone (#excelDropZone) must exist"

    # Verify file input exists
    file_input = page.query_selector("#excelFileInput")
    assert file_input is not None, "Excel file input element (#excelFileInput) must exist"

    save_screenshot(page, "r3", "excel_import_dropzone")

def test_r3_admin_excel_template_binary_download(cdp_session):
    page, cdp, tracker = cdp_session
    login_admin(page)

    # Fetch binary template via browser context request
    response = page.request.get(f"{BASE_URL}/api/admin/excel/template")
    assert response.status == 200, f"Expected 200 for excel template, got {response.status}"
    assert "spreadsheetml" in response.headers.get("content-type", "")

    body = response.body()
    result = validate_excel_binary(body)
    assert result["valid"] is True
    assert result["has_data"] is True
    assert len(result["sheet_names"]) > 0

    save_screenshot(page, "r3", "excel_template_downloaded")

def test_r3_admin_excel_inventory_export_binary(cdp_session):
    page, cdp, tracker = cdp_session
    login_admin(page)

    # Fetch inventory export via browser context request
    response = page.request.get(f"{BASE_URL}/api/admin/excel/export/inventory")
    assert response.status == 200, f"Expected 200 for inventory export, got {response.status}"
    assert "spreadsheetml" in response.headers.get("content-type", "")

    body = response.body()
    result = validate_excel_binary(body)
    assert result["valid"] is True
    assert result["has_data"] is True
    assert len(result["sheet_names"]) > 0

    save_screenshot(page, "r3", "excel_inventory_export")

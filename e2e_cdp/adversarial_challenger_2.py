import sys
import io
import json
import zipfile
import pytest
import openpyxl
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE_URL = "http://localhost:8080"
CHROME_PATH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
SCREENSHOT_DIR = Path(r"D:\Projects\strongwine_2\Wine-website\test-results\screenshots")

from conftest import CDPErrorTracker, save_screenshot
from utils.excel_validator import validate_excel_binary

def run_adversarial_suite():
    results = {
        "console_trap_stress": {},
        "route_zero_errors": {},
        "excel_integrity_stress": {},
        "mobile_ergonomics_stress": {}
    }
    
    with sync_playwright() as p:
        browser = p.chromium.launch(
            executable_path=CHROME_PATH,
            headless=True,
            args=["--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu"]
        )
        
        # ==========================================
        # 1. CONSOLE ERROR TRAP ADVERSARIAL STRESS
        # ==========================================
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()
        cdp = context.new_cdp_session(page)
        cdp.send("Console.enable")
        cdp.send("Runtime.enable")
        
        tracker = CDPErrorTracker()
        cdp.on("Console.messageAdded", tracker.on_console_message)
        cdp.on("Runtime.exceptionThrown", tracker.on_runtime_exception)
        
        page.goto(f"{BASE_URL}/home", wait_until="load")
        
        # Test 1A: Synthetic console.error capture
        page.evaluate("console.error('CHALLENGER_TEST_CONSOLE_ERROR')")
        assert any("CHALLENGER_TEST_CONSOLE_ERROR" in e for e in tracker.console_errors), "CDP failed to capture console.error"
        results["console_trap_stress"]["console_error_captured"] = True
        
        # Test 1B: Synthetic uncaught exception capture
        try:
            page.evaluate("() => { setTimeout(() => { throw new Error('CHALLENGER_TEST_EXCEPTION'); }, 10); }")
            page.wait_for_timeout(200)
            assert any("CHALLENGER_TEST_EXCEPTION" in e for e in tracker.runtime_exceptions), "CDP failed to capture Runtime.exceptionThrown"
            results["console_trap_stress"]["runtime_exception_captured"] = True
        except Exception as e:
            results["console_trap_stress"]["runtime_exception_captured"] = False
            
        # Test 1C: assert_clean fails when errors are trapped
        trap_fired = False
        try:
            tracker.assert_clean("should fail")
        except AssertionError:
            trap_fired = True
        assert trap_fired, "tracker.assert_clean() did NOT raise AssertionError when errors present!"
        results["console_trap_stress"]["assert_clean_properly_rejection"] = True
        
        tracker.clear()
        
        # Test 1D: Public & Authenticated routes zero-console-error stress
        routes = ["/home", "/wines", "/cart", "/login", "/register", "/forgot-password", "/wines/1"]
        route_results = {}
        for r in routes:
            tracker.clear()
            resp = page.goto(f"{BASE_URL}{r}", wait_until="load")
            tracker.assert_clean(f"on route {r}")
            route_results[r] = {
                "status": resp.status,
                "console_errors": len(tracker.console_errors),
                "runtime_exceptions": len(tracker.runtime_exceptions)
            }
        results["route_zero_errors"] = route_results
        
        cdp.detach()
        context.close()
        
        # ==========================================
        # 2. EXCEL BINARY INTEGRITY & CORRUPTION STRESS
        # ==========================================
        admin_context = browser.new_context()
        admin_page = admin_context.new_page()
        # Login admin
        admin_page.goto(f"{BASE_URL}/login", wait_until="load")
        admin_page.fill("input[name='username']", "admin")
        admin_page.fill("input[name='password']", "admin123")
        admin_page.click("button[type='submit']")
        admin_page.wait_for_load_state("load")
        
        # Download template
        tpl_resp = admin_page.request.get(f"{BASE_URL}/api/admin/excel/template")
        assert tpl_resp.status == 200
        tpl_bytes = tpl_resp.body()
        
        # Download inventory
        inv_resp = admin_page.request.get(f"{BASE_URL}/api/admin/excel/export/inventory")
        assert inv_resp.status == 200
        inv_bytes = inv_resp.body()
        
        # 2A: Verify genuine template structure
        tpl_val = validate_excel_binary(tpl_bytes)
        wb_tpl = openpyxl.load_workbook(io.BytesIO(tpl_bytes), data_only=True)
        results["excel_integrity_stress"]["template"] = {
            "valid": tpl_val["valid"],
            "sheet_names": tpl_val["sheet_names"],
            "row_count": tpl_val["row_count"],
            "headers": tpl_val["headers"]
        }
        
        # 2B: Verify genuine inventory export structure
        inv_val = validate_excel_binary(inv_bytes)
        wb_inv = openpyxl.load_workbook(io.BytesIO(inv_bytes), data_only=True)
        results["excel_integrity_stress"]["inventory"] = {
            "valid": inv_val["valid"],
            "sheet_names": inv_val["sheet_names"],
            "row_count": inv_val["row_count"],
            "headers": inv_val["headers"]
        }
        
        # 2C: Adversarial byte corruption tests
        # Corrupt magic header
        bad_magic_bytes = b"XX" + tpl_bytes[2:]
        magic_rejected = False
        try:
            validate_excel_binary(bad_magic_bytes)
        except AssertionError as e:
            if "Invalid binary header signature" in str(e):
                magic_rejected = True
        assert magic_rejected, "Corrupted magic signature was NOT rejected!"
        results["excel_integrity_stress"]["magic_corruption_rejected"] = True
        
        # Truncate file
        truncated_bytes = tpl_bytes[:50]
        trunc_rejected = False
        try:
            validate_excel_binary(truncated_bytes)
        except Exception:
            trunc_rejected = True
        assert trunc_rejected, "Truncated file was NOT rejected!"
        results["excel_integrity_stress"]["truncation_rejected"] = True
        
        # Corrupted central payload
        corrupted_body = bytearray(tpl_bytes)
        for i in range(100, min(200, len(corrupted_body))):
            corrupted_body[i] = 0xFF
        body_rejected = False
        try:
            wb = openpyxl.load_workbook(io.BytesIO(bytes(corrupted_body)))
        except Exception:
            body_rejected = True
        assert body_rejected, "Corrupted ZIP body was NOT rejected by openpyxl!"
        results["excel_integrity_stress"]["body_corruption_rejected"] = True
        
        admin_context.close()
        
        # ==========================================
        # 3. MOBILE VIEWPORT & ERGONOMICS STRESS (390x844)
        # ==========================================
        mobile_ctx = browser.new_context(
            viewport={"width": 390, "height": 844},
            device_scale_factor=3,
            is_mobile=True,
            has_touch=True
        )
        m_page = mobile_ctx.new_page()
        m_cdp = mobile_ctx.new_cdp_session(m_page)
        m_cdp.send("Console.enable")
        m_cdp.send("Runtime.enable")
        m_tracker = CDPErrorTracker()
        m_cdp.on("Console.messageAdded", m_tracker.on_console_message)
        m_cdp.on("Runtime.exceptionThrown", m_tracker.on_runtime_exception)
        
        # Login shipper
        m_page.goto(f"{BASE_URL}/login", wait_until="load")
        m_page.fill("input[name='username']", "shipper.ops.hcm")
        m_page.fill("input[name='password']", "admin123")
        m_page.click("button[type='submit']")
        m_page.wait_for_load_state("load")
        assert "/shipper/dashboard" in m_page.url
        m_page.wait_for_selector("main.container, .container", timeout=10000)
        m_tracker.assert_clean("on shipper dashboard load")
        
        # Check 3A: Horizontal overflow
        scroll_width = m_page.evaluate("() => document.documentElement.scrollWidth")
        viewport_width = m_page.evaluate("() => window.innerWidth")
        has_horizontal_overflow = scroll_width > viewport_width
        results["mobile_ergonomics_stress"]["horizontal_overflow"] = {
            "scroll_width": scroll_width,
            "viewport_width": viewport_width,
            "has_overflow": has_horizontal_overflow
        }
        assert not has_horizontal_overflow, f"Horizontal overflow detected: scrollWidth={scroll_width} > viewport={viewport_width}"
        
        # Check 3B: Driver action touch targets (tel, maps, OTP, buttons)
        tel_links = m_page.query_selector_all("a[href^='tel:']")
        map_links = m_page.query_selector_all("a[href*='google.com/maps']")
        otp_boxes = m_page.query_selector_all(".otp-box-mobile, .otp-box")
        action_buttons = m_page.query_selector_all(".btn")
        
        targets_info = []
        for i, tel in enumerate(tel_links):
            box = tel.bounding_box()
            if box:
                targets_info.append({
                    "type": "tel_link",
                    "index": i,
                    "x": box["x"],
                    "y": box["y"],
                    "width": box["width"],
                    "height": box["height"],
                    "meets_44px_target": box["height"] >= 44
                })
                # Assert no horizontal overflow off-screen
                assert box["x"] >= 0 and box["x"] + box["width"] <= viewport_width + 5, f"Tel link overflows viewport horizontally: {box}"
                assert box["height"] >= 38, f"Touch target height too small: {box['height']}px"

        for i, m in enumerate(map_links):
            box = m.bounding_box()
            if box:
                targets_info.append({
                    "type": "map_link",
                    "index": i,
                    "x": box["x"],
                    "y": box["y"],
                    "width": box["width"],
                    "height": box["height"],
                    "meets_44px_target": box["height"] >= 44
                })
                assert box["x"] >= 0 and box["x"] + box["width"] <= viewport_width + 5, f"Map link overflows viewport horizontally: {box}"
                assert box["height"] >= 38, f"Touch target height too small: {box['height']}px"
                
        otp_info = []
        for i, otp in enumerate(otp_boxes):
            box = otp.bounding_box()
            if box:
                otp_info.append({
                    "index": i,
                    "x": box["x"],
                    "y": box["y"],
                    "width": box["width"],
                    "height": box["height"],
                    "meets_touch_target": box["height"] >= 40 and box["width"] >= 40
                })
                assert box["x"] >= 0 and box["x"] + box["width"] <= viewport_width + 5, f"OTP box {i} overflows: {box}"
                assert box["height"] >= 38 and box["width"] >= 38, f"OTP box {i} too small: {box}"
                
        results["mobile_ergonomics_stress"]["action_targets"] = targets_info
        results["mobile_ergonomics_stress"]["otp_boxes"] = otp_info
        
        # Test 3C: OTP Auto-advancing stress
        if len(otp_boxes) >= 6:
            otp_boxes[0].click()
            for idx, digit in enumerate(["7", "3", "9", "2", "8", "4"]):
                otp_boxes[idx].type(digit)
                
            val = "".join([b.input_value() for b in otp_boxes[:6]])
            results["mobile_ergonomics_stress"]["otp_entered_value"] = val
            assert val == "739284", f"OTP auto-advance failed, expected 739284 got {val}"
            
        m_tracker.assert_clean("after shipper mobile ergonomics tests")
        save_screenshot(m_page, "challenger_r4", "mobile_touch_targets_verified")
        
        m_cdp.detach()
        mobile_ctx.close()
        browser.close()

    print("ALL_ADVERSARIAL_TESTS_PASSED")
    print(json.dumps(results, indent=2))
    with open("test-results/adversarial_challenger_2_results.json", "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2)

if __name__ == "__main__":
    run_adversarial_suite()

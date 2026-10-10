import os
import pytest
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE_URL = "http://localhost:8080"
CHROME_PATH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
SCREENSHOT_DIR = Path(r"D:\Projects\strongwine_2\Wine-website\test-results\screenshots")

SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)

class CDPErrorTracker:
    def __init__(self):
        self.console_errors = []
        self.runtime_exceptions = []

    def on_console_message(self, message):
        msg_type = message.get("message", {}).get("level", "")
        text = message.get("message", {}).get("text", "")
        source = message.get("message", {}).get("source", "")
        # Filter genuine JS console errors (ignore benign favicon / chrome-extension network 404s if any)
        if msg_type == "error":
            # Don't fail on intentional 403 test requests
            if "status of 403" not in text and "favicon.ico" not in text:
                self.console_errors.append(f"[{source}] {text}")

    def on_runtime_exception(self, event):
        exc_details = event.get("exceptionDetails", {})
        text = exc_details.get("text", "")
        exc = exc_details.get("exception", {})
        desc = exc.get("description", text)
        self.runtime_exceptions.append(desc)

    def assert_clean(self, context=""):
        errs = []
        if self.runtime_exceptions:
            errs.append(f"Runtime exceptions: {self.runtime_exceptions}")
        if self.console_errors:
            errs.append(f"Console errors: {self.console_errors}")
        assert not errs, f"Console/Runtime errors detected {context}: " + " | ".join(errs)

    def clear(self):
        self.console_errors.clear()
        self.runtime_exceptions.clear()


@pytest.fixture(scope="session")
def playwright_instance():
    with sync_playwright() as p:
        yield p


@pytest.fixture(scope="session")
def browser(playwright_instance):
    b = playwright_instance.chromium.launch(
        executable_path=CHROME_PATH,
        headless=True,
        args=["--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu"]
    )
    yield b
    b.close()


@pytest.fixture
def cdp_session(browser):
    context = browser.new_context(
        viewport={"width": 1440, "height": 900},
        ignore_https_errors=True
    )
    page = context.new_page()
    cdp = context.new_cdp_session(page)
    cdp.send("Console.enable")
    cdp.send("Runtime.enable")

    tracker = CDPErrorTracker()
    cdp.on("Console.messageAdded", tracker.on_console_message)
    cdp.on("Runtime.exceptionThrown", tracker.on_runtime_exception)

    yield page, cdp, tracker

    cdp.detach()
    context.close()


@pytest.fixture
def mobile_cdp_session(browser):
    context = browser.new_context(
        viewport={"width": 390, "height": 844},
        device_scale_factor=3,
        is_mobile=True,
        has_touch=True,
        ignore_https_errors=True
    )
    page = context.new_page()
    cdp = context.new_cdp_session(page)
    cdp.send("Console.enable")
    cdp.send("Runtime.enable")

    tracker = CDPErrorTracker()
    cdp.on("Console.messageAdded", tracker.on_console_message)
    cdp.on("Runtime.exceptionThrown", tracker.on_runtime_exception)

    yield page, cdp, tracker

    cdp.detach()
    context.close()


def save_screenshot(page, role: str, test_name: str) -> Path:
    path = SCREENSHOT_DIR / f"{role}_{test_name}.png"
    page.screenshot(path=str(path), full_page=True)
    return path

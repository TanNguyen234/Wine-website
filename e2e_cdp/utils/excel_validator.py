"""
Excel Validator Utility using openpyxl
"""
import io
import openpyxl

def validate_excel_binary(content: bytes) -> dict:
    """
    Validates that binary content is a valid .xlsx file and inspects sheets.
    Returns metadata dict: {'valid': True, 'sheets': [...], 'row_count': int, 'has_data': bool}
    """
    assert len(content) > 0, "Excel content is empty"
    # Verify ZIP / XLSX magic number: PK\x03\x04
    assert content.startswith(b"PK\x03\x04"), f"Invalid binary header signature: {content[:4]}"

    wb = openpyxl.load_workbook(io.BytesIO(content), data_only=True)
    sheet_names = wb.sheetnames
    assert len(sheet_names) > 0, "Excel workbook has no sheets"

    first_sheet = wb[sheet_names[0]]
    rows = list(first_sheet.iter_rows(values_only=True))
    row_count = len(rows)

    return {
        "valid": True,
        "sheet_names": sheet_names,
        "row_count": row_count,
        "has_data": row_count > 0,
        "headers": [str(c) for c in rows[0]] if row_count > 0 else []
    }

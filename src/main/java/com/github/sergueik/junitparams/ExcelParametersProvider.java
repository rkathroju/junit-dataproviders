package com.github.sergueik.junitparams;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
//OLE2 Office Documents
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
// conflicts with org.jopendocument.dom.spreadsheet.Cell;
// import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.util.CellReference;
//Office 2007+ XML
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
// Open Office Spreadsheet
import org.jopendocument.dom.ODDocument;
import org.jopendocument.dom.ODPackage;
import org.jopendocument.dom.ODValueType;
import org.jopendocument.dom.spreadsheet.Cell;
import org.jopendocument.dom.spreadsheet.Sheet;
import org.jopendocument.dom.spreadsheet.SpreadSheet;
import org.junit.runner.RunWith;
import org.junit.runners.model.FrameworkMethod;

import junitparams.JUnitParamsRunner;
import junitparams.custom.ParametersProvider;

/**
 * ExcelParametersProvider junitparams data providers for Excel and OpenOffice spreadsheet content 
 * @author: Serguei Kouzmine (kouzmine_serguei@yahoo.com)
 */
@RunWith(JUnitParamsRunner.class)
public class ExcelParametersProvider
		implements ParametersProvider<ExcelParameters> {

	private String filepath;
	private String filename;
	private String protocol;
	private String type;
	private String sheetName;
	private String columnNames = "*";
	// TODO: pass flag to skip / collect the first row through ExcelParameters
	// interface annotation - may be an overkill
	// private static Boolean skipFirstRow = false;

	@Override
	public void initialize(ExcelParameters parametersAnnotation,
			FrameworkMethod frameworkMethod) {
		filepath = parametersAnnotation.filepath();
		type = parametersAnnotation.type();
		sheetName = parametersAnnotation.sheetName();
		protocol = filepath.substring(0, filepath.indexOf(':'));
		filename = filepath.substring(filepath.indexOf(':') + 1);
	}

	@Override
	public Object[] getParameters() {
		return paramsFromFile();
	}

	private Object[] map(InputStream inputStream) {
		switch (type) {
		case "Excel 2007":
			return createDataFromExcel2007(inputStream);
		case "Excel 2003":
			return createDataFromExcel2003(inputStream);
		case "OpenOffice Spreadsheet":
			return createDataFromOpenOfficeSpreadsheet(inputStream);
		default:
			throw new RuntimeException("wrong format");
		}
	}

	private Object[] createDataFromOpenOfficeSpreadsheet(
			InputStream inputStream) {

		Map<String, String> columns = new HashMap<>();
		List<Object[]> result = new LinkedList<>();

		// Sheet sheet;
		// SpreadSheet spreadSheet;

		try {
			// https://www.programcreek.com/java-api-examples/index.php?api=org.jopendocument.dom.spreadsheet.Sheet
			SpreadSheet spreadSheet = SpreadSheet.get(new ODPackage(inputStream));
			Sheet sheet = (sheetName.isEmpty()) ? spreadSheet.getFirstSheet()
					: spreadSheet.getSheet(sheetName);

			// System.err
			// .println("Reading Open Office Spreadsheet : " + sheet.getName());

			int columnCount = sheet.getColumnCount();
			int rowCount = sheet.getRowCount();
			@SuppressWarnings("rawtypes")
			Cell cell = null;
			for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
				String columnHeader = sheet.getImmutableCellAt(columnIndex, 0)
						.getValue().toString();
				if (StringUtils.isBlank(columnHeader)) {
					break;
				}
				String columnName = CellReference.convertNumToColString(columnIndex);
				columns.put(columnName, columnHeader);
				/*
				System.err
						.println(columnIndex + " = " + columnName + " " + columnHeader);
				 */
			}
			// NOTE: often there may be no ranges defined
			Set<String> rangeeNames = sheet.getRangesNames();
			Iterator<String> rangeNamesIterator = rangeeNames.iterator();

			while (rangeNamesIterator.hasNext()) {
				System.err.println("Range = " + rangeNamesIterator.next());
			}
			// isCellBlank has protected access in Table
			for (int rowIndex = 1; rowIndex < rowCount && StringUtils.isNotBlank(sheet
					.getImmutableCellAt(0, rowIndex).getValue().toString()); rowIndex++) {
				List<Object> resultRow = new LinkedList<>();
				for (int columnIndex = 0; columnIndex < columnCount && StringUtils
						.isNotBlank(sheet.getImmutableCellAt(columnIndex, rowIndex)
								.getValue().toString()); columnIndex++) {
					cell = sheet.getImmutableCellAt(columnIndex, rowIndex);
					// TODO: column selection
					/*
					String cellName = CellReference.convertNumToColString(columnIndex);
					if (columns.get(cellName).equals("COUNT")) {
						assertEquals(cell.getValueType(), ODValueType.FLOAT);
						expected_count = Double.valueOf(cell.getValue().toString());
					}
					if (columns.get(cellName).equals("SEARCH")) {
						assertEquals(cell.getValueType(), ODValueType.STRING);
						search_keyword = cell.getTextValue();
					}
					if (columns.get(cellName).equals("ID")) {
						System.err.println("Column: " + columns.get(cellName));
						assertEquals(cell.getValueType(), ODValueType.FLOAT);
						id = Integer.decode(cell.getValue().toString());
					}
					*/
					@SuppressWarnings("unchecked")
					Object cellValue = safeOOCellValue(cell);
					/* System.err.println("Cell Value: " + cellValue.toString() + " "
							+ cellValue.getClass());
					*/
					resultRow.add(cellValue);
				}
				result.add(resultRow.toArray());
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
		return result.toArray();
	}

	private Object[] createDataFromExcel2003(InputStream inputStream) {

		List<Object[]> result = new LinkedList<>();
		HSSFWorkbook wb = null;
		Iterator<org.apache.poi.ss.usermodel.Cell> cells;
		Map<String, String> columnHeaders = new HashMap<>();

		try {
			wb = new HSSFWorkbook(inputStream);
			HSSFSheet sheet = wb.getSheetAt(0);

			/*	System.err
						.println("Reading Excel 2003 sheet : " + sheet.getSheetName());
			*/
			Iterator<Row> rows = sheet.rowIterator();
			while (rows.hasNext()) {
				HSSFRow row = (HSSFRow) rows.next();
				HSSFCell cell;

				if (row.getRowNum() == 0) {
					cells = row.cellIterator();
					while (cells.hasNext()) {

						cell = (HSSFCell) cells.next();
						int columnIndex = cell.getColumnIndex();
						String columnHeader = cell.getStringCellValue();
						String columnName = CellReference
								.convertNumToColString(cell.getColumnIndex());
						columnHeaders.put(columnName, columnHeader);

						/* System.err.println(
								 columnIndex + " = " + columnName + " " + columnHeader);
						*/
					}
					// skip the header
					continue;
				}

				cells = row.cellIterator();
				List<Object> resultRow = new LinkedList<>();
				while (cells.hasNext()) {
					cell = (HSSFCell) cells.next();
					Object cellValue = safeUserModeCellValue(cell);
					/* System.err.println("Cell Value: " + cellValue.toString() + " "
							+ cellValue.getClass());
					*/
					resultRow.add(cellValue);
				}
				result.add(resultRow.toArray());
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (wb != null) {
				try {
					wb.close();
				} catch (IOException e) {
				}
			}
		}
		return result.toArray();
	}

	private Object[] createDataFromExcel2007(InputStream inputStream) {

		List<Object[]> result = new LinkedList<>();
		Iterator<org.apache.poi.ss.usermodel.Cell> cells;
		XSSFWorkbook wb = null;
		Map<String, String> columnHeaders = new HashMap<>();

		try {
			wb = new XSSFWorkbook(inputStream);
			XSSFSheet sheet = (sheetName.isEmpty()) ? wb.getSheetAt(0)
					: wb.getSheet(sheetName);

			/*	System.err
						.println("Reading Excel 2007 sheet : " + sheet.getSheetName());
			*/

			Iterator<Row> rows = sheet.rowIterator();
			while (rows.hasNext()) {
				XSSFRow row = (XSSFRow) rows.next();
				XSSFCell cell;
				if (row.getRowNum() == 0) {
					cells = row.cellIterator();
					while (cells.hasNext()) {

						cell = (XSSFCell) cells.next();
						int columnIndex = cell.getColumnIndex();
						String columnHeader = cell.getStringCellValue();
						String columnName = CellReference
								.convertNumToColString(cell.getColumnIndex());
						columnHeaders.put(columnName, columnHeader);

						/*	System.err.println(
									columnIndex + " = " + columnName + " " + columnHeader);
						*/
					}
					// skip the header
					continue;
				}
				List<Object> resultRow = new LinkedList<>();
				cells = row.cellIterator();
				while (cells.hasNext()) {
					cell = (XSSFCell) cells.next();
					// TODO: column selection
					/*
					if (columns.get(cellColumn).equals("ID")) {
						assertEquals(cell.getCellType(), XSSFCell.CELL_TYPE_NUMERIC);
						// id = (int) cell.getNumericCellValue();
					}
					*/
					Object cellValue = safeUserModeCellValue(cell);
					// System.err.println("Cell Value: " + cellValue.toString() + " "
					// + cellValue.getClass());
					resultRow.add(cellValue);
				}
				result.add(resultRow.toArray());
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (wb != null) {
				try {
					wb.close();
				} catch (IOException e) {
				}
			}
		}
		return result.toArray();
	}

	private Object[] paramsFromFile() {
		try {
			InputStream inputStream = createProperReader();
			try {
				return map(inputStream);
			} finally {
				inputStream.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(
					"Could not successfully read parameters from file: " + filepath, e);
		}
	}

	private InputStream createProperReader() throws IOException {

		// System.err.println("createProperReader: " + filepath);
		if (filepath.indexOf(':') < 0) {
			return new FileInputStream(filepath);
		}

		if ("classpath".equals(protocol)) {
			return getClass().getClassLoader().getResourceAsStream(filename);
		} else if ("file".equals(protocol)) {
			return new FileInputStream(filename);
		}

		throw new IllegalArgumentException(
				"Unknown file access protocol. Only 'file' and 'classpath' are supported!");
	}

	// Safe conversion of type Excel cell object to Object / String value
	public static Object safeUserModeCellValue(
			org.apache.poi.ss.usermodel.Cell cell) {
		if (cell == null) {
			return null;
		}
		CellType type = cell.getCellTypeEnum();
		Object result;
		switch (type) {
		case _NONE:
			result = null;
			break;
		case NUMERIC:
			result = cell.getNumericCellValue();
			break;
		case STRING:
			result = cell.getStringCellValue();
			break;
		case FORMULA:
			throw new IllegalStateException("The formula cell is not supported");
		case BLANK:
			result = null;
			break;
		case BOOLEAN:
			result = cell.getBooleanCellValue();
			break;
		case ERROR:
			throw new RuntimeException("Cell has an error");
		default:
			throw new IllegalStateException(
					"Cell type: " + type + " is not supported");
		}
		return result;
		// return (result == null) ? null : result.toString();
	}

	// https://www.jopendocument.org/docs/org/jopendocument/dom/ODValueType.html
	public static Object safeOOCellValue(
			org.jopendocument.dom.spreadsheet.Cell<ODDocument> cell) {
		if (cell == null) {
			return null;
		}
		Object result;
		ODValueType type = cell.getValueType();
		switch (type) {
		case FLOAT:
			result = Double.valueOf(cell.getValue().toString());
			break;
		case STRING:
			result = cell.getTextValue();
			break;
		case TIME:
			result = null; // TODO
			break;
		case BOOLEAN:
			result = Boolean.getBoolean(cell.getValue().toString());
			break;
		default:
			throw new IllegalStateException("Can't evaluate cell value");
		}
		// return (result == null) ? null : result.toString();
		return result;
	}

}
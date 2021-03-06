package com.deepoove.poi.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deepoove.poi.NiceXWPFDocument;
import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.config.GramerSymbol;
import com.deepoove.poi.exception.RenderException;
import com.deepoove.poi.policy.RenderPolicy;
import com.deepoove.poi.policy.TextRenderPolicy;
import com.deepoove.poi.render.RenderAPI;
import com.deepoove.poi.resolver.TemplateResolver;
import com.deepoove.poi.template.ElementTemplate;
import com.deepoove.poi.template.run.RunTemplate;
import com.deepoove.poi.util.StyleUtils;

public class TableListRenderPolicy implements RenderPolicy
{
	private static Logger logger = LoggerFactory.getLogger(TableListRenderPolicy.class);

	public static String dataRegex = "\\$\\{(\\w+)\\}";
	public static Pattern dataPattern = Pattern.compile(dataRegex);

	public char getTag()
	{
		return GramerSymbol.TABLELIST.getSymbol();
	}

	public char getDataTag()
	{
		return GramerSymbol.TABLELISTCELL.getSymbol();
	}

	public String getTagStr(String tag)
	{
		return "{{" + getTag() + tag + "}}";
	}

	@Override
	public void render(ElementTemplate eleTemplate, Object data, XWPFTemplate template)
	{
		NiceXWPFDocument doc = template.getXWPFDocument();
		RunTemplate runTemplate = (RunTemplate) eleTemplate;
		XWPFRun run = runTemplate.getRun();
		try
		{
			XmlCursor newCursor = ((XWPFParagraph) run.getParent()).getCTP().newCursor();
			newCursor.toParent();
			// if (newCursor.getObject() instanceof CTTc)
			newCursor.toParent();
			newCursor.toParent();
			XmlObject object = newCursor.getObject();
			XWPFTable table = doc.getTable((CTTbl) object);
			render(table, data, runTemplate.getTagName(), template);
		}
		catch (Exception e)
		{
			logger.error("dynamic table error:" + e.getMessage(), e);
		}
	}

	public void render(XWPFTable table, Object data, String tagName, XWPFTemplate template)
	{
		List<XWPFTableRow> rowList = table.getRows();
		int index = 0;
		boolean findFlag = false;
		String tableListValue = "";
		for (; index < rowList.size(); index++)
		{
			XWPFTableRow xtr = rowList.get(index);
			List listTableCells = xtr.getTableCells();
			for (int j = 0; j < listTableCells.size(); j++)
			{
				XWPFTableCell cell0 = xtr.getCell(j);
				tableListValue = cell0.getText();
				if (cell0 != null && tableListValue.contains(getTagStr(tagName)))
				{
					table.removeRow(index);
					findFlag = true;
					break;
				}
			}
			if (findFlag)
			{
				break;
			}
		}

		if (!findFlag)
		{
			return;
		}

		//循环的行数
		tableListValue = tableListValue.replace(getTagStr(tagName), "");
		
		if (!validateNumber(tableListValue))
		{
			return;
		}
		int copyRow = Integer.parseInt(tableListValue);

		try
		{
			if (data == null)
			{
				return;
			}

			List dataList = (List) data;
			if (dataList.isEmpty())
			{
				return;
			}
			//将数据按顺序插入table
			Collections.reverse(dataList);
			List<XWPFTableRow> copyRowList = new ArrayList<XWPFTableRow>();
			for (int i = 0; i < copyRow; i++)
			{
				//取出所有待循环row
				copyRowList.add(table.getRow(index - 1 - i));
			}

			Map dataMap = null;
			for (int i = 0; i < dataList.size(); i++)
			{
				Object dataObj = dataList.get(i);
				if (dataObj instanceof Map)
				{
					dataMap = (Map) dataObj;
				}
				else
				{
					dataMap = RenderAPI.convert2Map(dataObj);
				}
				for (int j = 0; j < copyRowList.size(); j++)
				{
					XWPFTableRow xtr = copyRowList.get(j);
					//复制行并且复制样式和替换数据
					XWPFTableRow xtrNew = table.insertNewTableRow(index);
					copyPro(xtr, xtrNew);
					changeRowValue(template, dataMap, xtrNew);
				}
			}
		}
		finally
		{
			//删除循环row
			deleteModel(table, index, copyRow);
		}


	}

	private void deleteModel(XWPFTable table, int index, int copyRow)
	{
		for (int i = 0; i < copyRow; i++)
		{
			table.removeRow(index - 1 - i);
		}
	}

	private void copyPro(XWPFTableRow sourceRow, XWPFTableRow targetRow)
	{
		// 复制行属性
		targetRow.getCtRow().setTrPr(sourceRow.getCtRow().getTrPr());
		List<XWPFTableCell> cellList = sourceRow.getTableCells();
		if (null == cellList)
		{
			return;
		}
		// 添加列、复制列以及列中段落属性
		XWPFTableCell targetCell = null;
		for (XWPFTableCell sourceCell : cellList)
		{
			targetCell = targetRow.addNewTableCell();
			// 列属性
			targetCell.getCTTc().setTcPr(sourceCell.getCTTc().getTcPr());
			// 段落属性
			targetCell.getParagraphs().get(0).getCTP().setPPr(sourceCell.getParagraphs().get(0).getCTP().getPPr());
			List<XWPFRun> runs = sourceCell.getParagraphs().get(0).getRuns();
			for (int i = 0; i < runs.size(); i++)
			{
				XWPFRun extraRun = targetCell.getParagraphs().get(0).insertNewRun(i);
				StyleUtils.styleRun(extraRun, runs.get(i));
				extraRun.setText(runs.get(i).getText(0), 0);
			}
			// targetCell.setText(sourceCell.getText());
			// XWPFRun
		}
	}

	private void changeRowValue(XWPFTemplate template, Map dataMap, XWPFTableRow formatRow)
	{
		Configure config = Configure.createDefault().plugin(getDataTag(), new TextRenderPolicy());
		TemplateResolver tr = new TemplateResolver(config);
		List<ElementTemplate> elementTemplates = tr.parseTableRow(formatRow);
		RenderPolicy policy = null;
		for (ElementTemplate runTemplate : elementTemplates)
		{
			logger.debug("TagName:{}, Sign:{}", runTemplate.getTagName(), runTemplate.getSign());
			policy = config.getPolicy(runTemplate.getTagName(), runTemplate.getSign());
			if (null == policy)
				throw new RenderException("cannot find render policy: [" + runTemplate.getTagName() + "]");
			policy.render(runTemplate, dataMap.get(runTemplate.getTagName()), template);
		}
	}

	/**
	 * 
	 * @Title: validateNumber
	 * @Description: 检查是否全数字
	 * @param @param number
	 * @param @return
	 * @return boolean
	 * @throws
	 */
	public static boolean validateNumber(String number)
	{
		boolean flag = false;
		if (number != null)
		{
			Matcher m = null;
			Pattern p = Pattern.compile("^[0-9]+$");
			m = p.matcher(number);
			flag = m.matches();
		}

		return flag;

	}
}

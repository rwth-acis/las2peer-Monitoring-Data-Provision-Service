package i5.las2peer.services.monitoring.provision.successModel.visualizations.charts;

import java.util.Iterator;

/**
*
* Converts a {@link MethodResult} into a Google Line-Chart.
*
* Data Format:
* <br>
* The index of the X-Axis is the first Column, the other columns are represented as lines
* (So the format has to be "Anything";Number 1,...,Number n)
*
*/
public class LineChart{
	
	private String resultHTML = null;
	
	/**
	*
	* Constructor of a LineChart.
	*
	* @param methodResult
	* @param visualizationParameters a String of parameters: [div-Id, title, height, width]
	*
	*/
	public LineChart(MethodResult methodResult, String[] visualizationParameters){
		
		String divId = visualizationParameters[0];
		String title = visualizationParameters[1];
		String height = visualizationParameters[2];
		String width = visualizationParameters[3];
		
		String[] columnNames = methodResult.getColumnNames();
		Integer[] columnTypes = methodResult.getColumnDatatypes();
		Iterator<Object[]> iterator = methodResult.getRowList().iterator();
		
		int columnCount = columnTypes.length;
		
		
		resultHTML = "<div id='" + divId + "' style='height: "+ height + "; width: " + width + ";'></div>\n";
		resultHTML += "<script>\n";			
		resultHTML += "var qv_script = document.createElement('script');\n";
		resultHTML += "qv_script.src = 'https://www.google.com/jsapi?callback=qv_loadChart';\n";
		resultHTML += "qv_script.type = 'text/javascript';\n";
		resultHTML += "document.getElementsByTagName('head')[0].appendChild(qv_script);\n";
		resultHTML += "function qv_loadChart(){\n";
		resultHTML += "google.load('visualization', '1', {packages: ['corechart'], callback: qv_drawChart});\n";
		resultHTML += "}\n";
		resultHTML += "function qv_drawChart() {\n";
		resultHTML += "var data = google.visualization.arrayToDataTable([\n";
		
		//Column Names
		resultHTML += "[";
		for(int i = 0; i < columnCount-1; i++){
			resultHTML += "'" + columnNames[i] + "', ";
		}
		
		resultHTML += "'" + columnNames[columnCount-1] + "'],\n";
		
		String[] currentRowEntries = new String[columnCount];
		while(iterator.hasNext()){
			Object[] currentRow = iterator.next();
			for(int i = 0; i < columnCount; i++){
				currentRowEntries[i] = currentRow[i].toString();
			}
			//First entry has to be a String
			resultHTML += "['" + currentRowEntries[0] + "', ";
			for(int j = 1; j < columnCount-1; j++){
				resultHTML += currentRowEntries[j] + ", ";
			}
			if(iterator.hasNext())
				resultHTML += currentRowEntries[columnCount-1] + "],\n";
			else
				//Last Entry
				resultHTML += currentRowEntries[columnCount-1] + "]\n";
		}
		resultHTML += "]);\n";
		
		resultHTML += "var options = {\n";
		resultHTML += "'title':'" + title + "',\n";
		resultHTML += "};\n";
		
		resultHTML += "var chart = new google.visualization.LineChart(document.getElementById('" + divId + "'));\n";
		resultHTML += "chart.draw(data, options);\n";
		
		resultHTML += "}\n</script>";
		
	}
	
	
	/**
	*
	* Gets the HTML representation of this chart.
	*
	* @return the HTML representation as a string
	*
	*/
	public String getResultHTML(){
		return this.resultHTML;
	}
	
	
}

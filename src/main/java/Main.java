import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

public class Main {

    private static String siteLink = "https://www.moscowmap.ru/metro.html#lines";
    private static String moscowMetroMap = "src/main/java/data/moscowMetro.json";
    private static String docLink = "src/main/java/data/source.html";

    public static void main(String[] args) {

        try {
            Document doc = Jsoup.connect(siteLink).maxBodySize(0).timeout(100000).get(); //Получаем в doc html данные с сайта

            //File input = new File(docLink);//
            //Document doc = Jsoup.parse(input, "UTF-8", siteLink);//

            Elements lines = doc.select("span.js-metro-line"); //Получаем линии
            Elements stations = doc.select("div.js-metro-stations");//Получаем все станции всех линий
            Elements connections = doc.select("div.js-metro-stations >p >a >span.t-icon-metroln");//Получаем все задублированные переходы

            JSONParser parser = new JSONParser();
            JSONObject jsonData;

            if (!Files.exists(Paths.get(moscowMetroMap))){ //Если файла нет, создаем
                Files.createFile(Paths.get(moscowMetroMap));
            }

            jsonData = Files.size(Paths.get(moscowMetroMap)) > 0 ? (JSONObject) parser.parse(getJSONFile()) : new JSONObject(); //Если файл  пустой, создаем JSONObject, иначе - считываем

            if (lines.size() > 0) { //Проверяем, что вообще есть линии метро для добавления и записываем в файл
                addLinesToJSONObject(jsonData, lines);
            }

            if (stations.size() > 0){ //Проверяем, что есть станции и записываем в файл. В решении используется предположение, что в данных по станциям метро отсутствуют линии, не указанные в перечне линий.
                addStationsToJSONObject(jsonData, stations, lines);
            }

            if(connections.size() > 0){
                addConnectionsToJSONObject(jsonData, doc, lines);
            }

            printStationAmountForLines();
            printConnectionAmount();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void addLinesToJSONObject(JSONObject jsonObject, Elements lines){
        JSONArray linesArray = jsonObject.get("lines") == null ? new JSONArray() : (JSONArray) jsonObject.get("lines");

        if(!linesArray.isEmpty()){ //В файле есть данные
            linesArray.clear();
        }

        for(Element line : lines){ //для каждой линии создаем обект и добавляем в него ключи и значения
            JSONObject lineData = new JSONObject();
            lineData.put("number", line.attr("data-line"));
            lineData.put("name", line.text());
            linesArray.add(lineData); //Добавляем объект в общий массив
        }
        jsonObject.put("lines", linesArray);
        try { //Записываем в файл
            ObjectMapper mapper = new ObjectMapper();
            FileWriter writer = new FileWriter(moscowMetroMap);
            writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject));
            writer.flush();
            writer.close();
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
    }

    public static void addStationsToJSONObject(JSONObject jsonObject, Elements allStations, Elements lines){
        JSONObject line2stations = new JSONObject();
        //Структура хранения станций такова: это объект, значением которого является объект, где ключ - номер линии метро, значение - массив названий станций. Предполагаем, что не могут быть использованы линии кроме тех,
        //что есть в перечне линий. Для каждой линии определяем названия её станций и помещаем в массив, добавляем полученное сочетание ключ-значение в объект.

        for(Element line : lines){
            JSONArray lineStations = new JSONArray();
            Elements currentLineStations = allStations.select("div.js-metro-stations[data-line = " + line.attr("data-line") + "] >p >a >span.name"); //Отбираем названия станций текущей линии метро
            //System.out.println("Добавляем станции линии " + line.text());
            for(Element station : currentLineStations){
                //System.out.println(station.text());
                lineStations.add(station.text()); //Добавляем станции в массив
            }
            line2stations.put(line.attr("data-line"), lineStations); //Добавляем соответствие Линия - массив станций
        }
        jsonObject.put("stations", line2stations);
        try { //Записываем в файл
            ObjectMapper mapper = new ObjectMapper();
            FileWriter writer = new FileWriter(moscowMetroMap);
            writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject));
            writer.flush();
            writer.close();
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private static void addConnectionsToJSONObject(JSONObject jsonObject, Document doc, Elements lines){
        JSONArray connectionsArray = jsonObject.get("connections") == null ? new JSONArray() : (JSONArray) jsonObject.get("connections");
        if(!connectionsArray.isEmpty()){ //В файле есть данные
            connectionsArray.clear();
        }

        TreeMap<Station, TreeSet<Station>> connections = getUniqueConnections(lines, doc);

        for(Station station : connections.keySet()){
            JSONObject stationFrom = new JSONObject();
            stationFrom.put("line", station.getLineNumber());
            stationFrom.put("station", station.getName());



            connections.get(station).forEach(station1 -> {
                JSONObject stationTo = new JSONObject();
                stationTo.put("line", station1.getLineNumber());
                stationTo.put("station", station1.getName());

                JSONArray connection = new JSONArray();
                connection.add(stationFrom);
                connection.add(stationTo);
                connectionsArray.add(connection);
            });

        }

        jsonObject.put("connections", connectionsArray);
        try { //Записываем в файл
            ObjectMapper mapper = new ObjectMapper();
            FileWriter writer = new FileWriter(moscowMetroMap);
            writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject));
            writer.flush();
            writer.close();
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private static TreeMap<Station, TreeSet<Station>> getUniqueConnections(Elements lines, Document doc){
        TreeMap<Station, TreeSet<Station>> connections = new TreeMap<>();
        for(Element line : lines) {
            Elements currentLineStationsAndConnections = doc.select("div.js-metro-stations[data-line = " + line.attr("data-line") + "] >p >a >span.name, div.js-metro-stations[data-line = " + line.attr("data-line") + "] >p >a >span.t-icon-metroln");
            //В полученном перечне элементов содержится информация о станциях, с которых переходим и на которые переходим. Необходимо исключить танции без переходов
            Elements currentLineConnections = getCurrentLineConnections(currentLineStationsAndConnections);
            //currentLineConnections.forEach(System.out::println);
            if(currentLineConnections.size() > 0){
                //System.out.println("Все переходы линии " + line.text());

                for(int i = 0; i < currentLineConnections.size() - 2; i = i + 2){
                    Station stationFrom = new Station(
                            line.attr("data-line"),
                            currentLineConnections.get(i).text()
                    );
                    Station stationTo = new Station(
                            currentLineConnections.get(i + 1).attr("class").substring("t-icon-metroln ln-".length()),
                            currentLineConnections.get(i + 1).attr("title").substring("переход на станцию @".length(), currentLineConnections.get(i + 1).attr("title").lastIndexOf('»'))
                    );

                    TreeSet <Station> connectedStations = new TreeSet<>();
                    if(connections.size() > 0) {
                        if (connections.get(stationFrom) != null) {
                            connectedStations = connections.get(stationFrom);
                        }
                        if (connections.get(stationTo) != null) {
                            connectedStations = connections.get(stationTo);
                            connectedStations.add(stationFrom);
                            connections.put(stationTo, connectedStations);
                            continue;
                        }
                    }
                    connectedStations.add(stationTo);
                    connections.put(stationFrom, connectedStations);
                }
            }
        }

        //Вывод всех переходов
        /*for(Station station : connections.keySet()){
            System.out.println("Переходы на станции " + station.ToString() + ":");
            connections.get(station).forEach(st -> System.out.println("\t" + st.ToString()));
        }*/

        return connections;
    }

    private static Elements getCurrentLineConnections(Elements allElements){
        Elements data = new Elements();
        int i = 0;
        Element lastAddedStation = null;
        while(i < allElements.size() - 1){
            Element currentData = allElements.get(i);
            Element nextData = allElements.get(i + 1);
            if(currentData.hasClass("name") && nextData.hasAttr("title")){ //проверяем стандартный переход с 1 станции на 1 станцию
                data.add(currentData);
                data.add(nextData);
                i += 2;
                lastAddedStation = currentData;
            }
            else if(currentData.hasAttr("title") && lastAddedStation != null){//проверяем случай перехода с одной станиции на несколько, записываем все комбинации
                data.add(lastAddedStation);
                data.add(currentData);
                i++;
            }
            else{
                i ++;
            }
        }
        //data.forEach(System.out::println);
        return data;
    }

    private static String getJSONFile() {
        StringBuilder builder = new StringBuilder();
        try {
            List<String> lines = Files.readAllLines(Paths.get(moscowMetroMap));
            if(lines.size() == 0){
                return "";
            }
            lines.forEach(builder::append);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return builder.toString();
    }

    private static void printStationAmountForLines(){
        JSONParser parser = new JSONParser();
        try {
            JSONObject jsonObject = (JSONObject) parser.parse(getJSONFile());
            JSONObject stationsObject = (JSONObject) jsonObject.get("stations");
            JSONArray linesArray = (JSONArray) jsonObject.get("lines");

            linesArray.forEach(lineObject -> {
                JSONObject lineJsonObject = (JSONObject) lineObject;
                int stationAmount = ((JSONArray) stationsObject.get(lineJsonObject.get("number"))).size();
                System.out.println("Линия метро \"" + lineJsonObject.get("name") + "\" содержит следующее количество станций : " + stationAmount);
            });
        }
        catch(Exception ex){
            ex.printStackTrace();
        }
    }

    private static void printConnectionAmount(){
        JSONParser parser = new JSONParser();
        try{
            JSONObject jsonObject = (JSONObject) parser.parse(getJSONFile());
            JSONArray connectionsArray = (JSONArray) jsonObject.get("connections");
            System.out.println("В метро имеется " + connectionsArray.size() + " переходов");
        }
        catch(Exception ex){
            ex.printStackTrace();
        }
    }

    private static void printElements(Elements elements){
        elements.forEach(System.out::println);
    }

}

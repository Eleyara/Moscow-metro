public class Station implements Comparable<Station> {

    private final String lineNumber;

    private final String name;

    public Station(String lineNumber, String name){
        this.lineNumber = lineNumber;
        this.name = name;
    }

    public String getLineNumber() {
        return lineNumber;
    }

    public String getName() {
        return name;
    }

    public String ToString(){
        return "Станция " + name + " линия " + lineNumber;
    }

    public boolean stationsEqual(Station station){
        if(station == null){
            return false;
        }
        return this.name.equals(station.getName()) && this.lineNumber.equals(station.getLineNumber());
    }

    @Override
    public int compareTo(Station station) {
        if(station == null){
            return -1;
        }

        return lineNumber.compareTo(station.getLineNumber()) == 0 ? name.compareTo(station.getName()) : lineNumber.compareTo(station.getLineNumber());
    }
}

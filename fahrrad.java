import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Fahrrad {

    private int fahrradId;
    private String fahrradtyp;
    private String farbe;
    private String status;
    private double preisProStunde;

    public Fahrrad(int fahrradId, String fahrradtyp, String farbe,
                   String status, double preisProStunde) {
        this.fahrradId = fahrradId;
        this.fahrradtyp = fahrradtyp;
        this.farbe = farbe;
        this.status = status;
        this.preisProStunde = preisProStunde;
        // test als uebung - mein test
    }

    @Override
    public String toString() {
        return "ID: " + fahrradId
                + ", Typ: " + fahrradtyp
                + ", Farbe: " + farbe
                + ", Status: " + status
                + ", Preis pro Stunde: " + preisProStunde + " Euro";
    }

    public static void main(String[] args) {
        String datei = "fahrrad.csv";

        try (BufferedReader reader = new BufferedReader(new FileReader(datei))) {
            String zeile;

            // Kopfzeile überspringen
            reader.readLine();

            while ((zeile = reader.readLine()) != null) {
                String[] daten = zeile.split(",");

                int fahrradId = Integer.parseInt(daten[0]);
                String fahrradtyp = daten[1];
                String farbe = daten[2];
                String status = daten[3];
                double preisProStunde = Double.parseDouble(daten[4]);

                Fahrrad fahrrad = new Fahrrad(
                        fahrradId,
                        fahrradtyp,
                        farbe,
                        status,
                        preisProStunde
                );

                System.out.println(fahrrad);
            }

        } catch (IOException | NumberFormatException e) {
            System.out.println("Fehler beim Einlesen der CSV-Datei: " + e.getMessage());
        }
    }
}
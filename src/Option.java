import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class Option extends Asset {
    private String order_id;
    private Database database;
    private String type;
    private String action;
    private double strike_price;
    private String signing_date;
    private String exercise_date;
    private double volatility;
    private double risk_free_rate;
    private double signing_price;
    private int number_of_stocks;

    Option(String order_id,Database db,String type, String action, double strike_price, String signing_date, String exercise_date, double volatility, double current_price, int number_of_stocks) {
        this.database = db;
        this.order_id = order_id;

        if (type.equals("Call") || type.equals("Put")) {
            this.type = type;
        }
        if (action.equals("Buy") || action.equals("Sell")) {
            this.action = action;
        }
        this.strike_price = Math.max(0,strike_price); //Strike price can not be negative

        //Is the signing date before the exercise date?
        if (is_before(signing_date, exercise_date)) {
            this.signing_date = signing_date;
            this.exercise_date = exercise_date;
        }
        this.volatility = volatility;

        this.risk_free_rate = Math.pow(1+database.get_treasury_value(signing_date),1/date_dif())-1; //for this period

        if (current_price > 0){
            this.signing_price = current_price;
        }
        if (number_of_stocks > 0){
            this.number_of_stocks = number_of_stocks;
        }
    }

    //Helper functions
    private boolean is_before(String signing_date, String exercise_date) {
        //Source: https://stackoverflow.com/questions/19109960/how-to-check-if-a-date-is-greater-than-another-in-java

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date date1,date2;
        try {
            date1 = sdf.parse(signing_date);
            date2 = sdf.parse(exercise_date);
            return !date1.after(date2);
        } catch (ParseException e) {
            System.out.println("Signing date and exercise date are not properly formatted.");
        }
        return false;


    }
    private double date_dif(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date date1,date2;

        try {
            date1 = sdf.parse(signing_date);
            date2 = sdf.parse(exercise_date);
            return Math.abs(date2.getTime()-date1.getTime())/(86400000);
        } catch (ParseException e) {
            System.out.println("Signing date and exercise date are not properly formatted.");
        }

        return Double.parseDouble(null);

    }
    private double CND(double X) {
        //SOURCE: https://github.com/bret-blackford/black-scholes/blob/master/OptionValuation/src/mBret/options/Black_76.java
        double L, K, w;
        double a1 = 0.31938153, a2 = -0.356563782, a3 = 1.781477937, a4 = -1.821255978, a5 = 1.330274429;

        L = Math.abs(X);
        K = 1.0 / (1.0 + 0.2316419 * L);
        w = 1.0
                - 1.0
                / Math.sqrt(2.0 * Math.PI)
                * Math.exp(-L * L / 2)
                * (a1 * K + a2 * K * K + a3 * Math.pow(K, 3) + a4
                * Math.pow(K, 4) + a5 * Math.pow(K, 5));

        if (X < 0.0) {
            w = 1.0 - w;
        }
        return w;
    }
    public String get_Action(){
        return action;
    }
    public String get_Type(){
        return type;
    }
    public double get_Strike(){
        return strike_price;
    }
    public int get_number_of_Stocks(){
        return number_of_stocks;
    }
    public String getExercise_date(){
        return exercise_date;
    }
    public boolean can_be_exercised_today(String current_date) {
        return current_date.equals(this.exercise_date);
    }



    @Override
    public double get_Asset_Value(String current_date) {

        double current_market_price =  database.get_stock_value(current_date,"Open");

        if (!is_before(current_date,exercise_date)){
            System.out.println("Current date: "+current_date+ "exercise date: "+exercise_date);
        }

        switch (this.type){
            case ("Call"):
                if (this.action.equals("Buy")){
                    return Math.max(current_market_price-this.strike_price,0)*this.number_of_stocks;
                }
                //SELL
                return -1*Math.max(current_market_price-this.strike_price,0)*this.number_of_stocks;

            case ("Put"):
                if (this.action.equals("Buy")){
                    return Math.max(0,this.strike_price-current_market_price)*number_of_stocks;
                }
                //SELL
                return -1*Math.max(0,this.strike_price-current_market_price)*number_of_stocks;


        }
        System.out.println("Error calculating options market value");
        return 0;
    }


    @Override
    public double exercise_value(String current_date){
        switch (this.action){
            case ("Buy"):
                if (get_Asset_Value(current_date) > 0){
                    return this.strike_price;
                }
                return 0;
            case ("Sell"):
                if (get_Asset_Value(current_date) < 0){
                    return database.get_stock_value(current_date,"Open");
                }
                return 0;
        }
        System.out.println("Error calculating options excersise expense");
        return 0;
    }

    @Override
    public String get_Name() {
        return "Option";
    }
    @Override
    public int get_Quantity() {
        return 1;
    }

    public double option_premium_BS() {
        //SOURCE: https://github.com/bret-blackford/black-scholes/blob/master/OptionValuation/src/mBret/options/Black_76.java

        //Black-Scholes formula

        double d1 = (Math.log(this.signing_price / this.strike_price) + (this.risk_free_rate + this.volatility * this.volatility / 2) * date_dif()) / (this.volatility * Math.sqrt(date_dif()));
        double d2 = d1 - this.volatility * Math.sqrt(date_dif());

        DecimalFormat df = new DecimalFormat("#.0000");

        switch (this.type) {
            case "Call":
                //Rounding value to 3 decimal places
                return Double.valueOf(df.format(this.signing_price * CND(d1) - this.strike_price * Math.exp(-this.risk_free_rate * date_dif()/365) * CND(d2)));
            case "Put":
                //Rounding value to 3 decimal places
               return Double.valueOf(df.format(this.strike_price * Math.exp(-this.risk_free_rate * date_dif()/365) * CND(-d2) - this.signing_price * CND(-d1)));
            default:
                System.out.println("Error calculation option premium");
                return 0;
        }

    }

    public String getOrder_id(){
        return order_id;
    }

    }


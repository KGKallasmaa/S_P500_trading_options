import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;


@SuppressWarnings("Duplicates")
public class Database {

    private HashMap<String,Double> high_data; //(date,value) RSI
    private HashMap<String,Double> low_data; //(date,value) RSI
    private HashMap<String,Double> unemployment_data; //(date,value)
    private LinkedHashMap<String,Double> stock_data; //(date,value)
    private LinkedHashMap<String,Double> vix_data; //(date,value)
    private LinkedHashMap<String,Double> pe_data; //(date,value)
    private LinkedHashMap<String,Double> treasury_data; //(date,value)
    private LinkedHashMap<String,Double> inflation_data; //(date,value)
    private LinkedHashMap<String,Double> dividend_data; //(date,value)
    private List<Long> trading_time;
    private final String log_file_name;
    private final String signal_file_name;
    private final HashMap<String,String> order_id_action;

    Database(String vix_file_name,String treasury_rate_file_name,String signal_file_name,String pe_file_name,String unemployment_file_name,String stock_file_name,String log_file_name,String div_file_name,String inflation_file_name){
        this.log_file_name = log_file_name;
        this.signal_file_name = signal_file_name;
        this.trading_time = new ArrayList<>();
        this.order_id_action = new HashMap<>();



        if (Files.exists(Paths.get(vix_file_name))) {
            this.vix_data = new LinkedHashMap<>();
            try {
                Scanner scanner = new Scanner(new File(vix_file_name));
                scanner.nextLine();
               while (scanner.hasNext()) {
                    String line = scanner.nextLine();

                    List<String> line_list = new ArrayList<>(Arrays.asList(line.split(",")));

                    //Data is already propery formated

                    this.vix_data.put(line_list.get(0), Double.parseDouble(line_list.get(1)));

                }
                scanner.close();
            } catch (FileNotFoundException e) {
                System.out.println("VIX file was not found");
            }
        }






        if (Files.exists(Paths.get(stock_file_name))) {
            this.stock_data = new LinkedHashMap<>();
            this.high_data = new HashMap<>();
            this.low_data = new HashMap<>();
            try {
                Scanner scanner = new Scanner(new File(stock_file_name));
                scanner.nextLine();
                while (scanner.hasNext()) {
                    String line = scanner.nextLine();

                    List<String> line_list = new ArrayList<>(Arrays.asList(line.split(",")));

                    //Extracting proper date
                    //Sample =  03/01/1950,16.66
                    List<String> date_split = new ArrayList<>(Arrays.asList(line_list.get(0).split("/")));
                    String sb = date_split.get(2) + "-" + date_split.get(1) + "-" + date_split.get(0);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    Date date;
                    try {
                        date = sdf.parse(sb);
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(date);
                        trading_time.add(calendar.getTimeInMillis());
                    } catch (ParseException e) {
                        System.out.println("Error: String ("+sb+") couldn' be converted to date");
                    }

                    this.stock_data.put(sb, Double.parseDouble(line_list.get(1)));
                    this.high_data.put(sb, Double.parseDouble(line_list.get(2)));
                    this.low_data.put(sb, Double.parseDouble(line_list.get(3)));
                }
                scanner.close();
            } catch (FileNotFoundException e) {
                System.out.println("Stock file was not found");
            }
        }

        if (Files.exists(Paths.get(inflation_file_name))) {
            this.inflation_data = new LinkedHashMap<>();
            Scanner scanner;
            try {
                scanner = new Scanner(new File(inflation_file_name));
                scanner.nextLine();
                while (scanner.hasNext()) {
                    String line = scanner.nextLine();

                    List<String> line_list = new ArrayList<>(Arrays.asList(line.split(",")));

                    //Extracting proper date
                    //Sample =  03/01/1950,16.66
                    List<String> date_split = new ArrayList<>(Arrays.asList(line_list.get(0).split("/")));
                    String sb = date_split.get(2)+"-" +date_split.get(1)+"-"+date_split.get(0);
                    this.inflation_data.put(sb, Double.parseDouble(line_list.get(1)));
                }
            } catch (FileNotFoundException e) {
                System.out.println("Inflation file was not found");
            }
        }

        if (Files.exists(Paths.get(div_file_name))) {
            this.dividend_data = new LinkedHashMap<>();
            Scanner scanner;
            try {
                scanner = new Scanner(new File(div_file_name));
                scanner.nextLine();
                while (scanner.hasNext()) {
                    String line = scanner.nextLine();

                    List<String> line_list = new ArrayList<>(Arrays.asList(line.split(",")));

                    //Extracting proper date
                    //Sample =  03/01/1950,16.66
                    List<String> date_split = new ArrayList<>(Arrays.asList(line_list.get(0).split("/")));
                    String year_end_dividend_date = date_split.get(2)+"-" +date_split.get(1)+"-"+date_split.get(0);

                    Double yearly_dividend_normalized = from_feb2018_to_right_historic_value(year_end_dividend_date,Double.parseDouble(line_list.get(1)));

                    String first_quarter_dividend_date = dividend_weekend_to_monday(LocalDate.parse(year_end_dividend_date).plusDays(-365/4*3).toString());
                    String second_quarter_dividend_date= dividend_weekend_to_monday(LocalDate.parse(year_end_dividend_date).plusDays(-365/4*2).toString());
                    String third_quarter_dividend_date= dividend_weekend_to_monday(LocalDate.parse(year_end_dividend_date).plusDays(-365/4).toString());
                    String fourth_quarter_dividend_date= dividend_weekend_to_monday(year_end_dividend_date);


                    this.dividend_data.put(first_quarter_dividend_date,yearly_dividend_normalized/4);
                    this.dividend_data.put(second_quarter_dividend_date,yearly_dividend_normalized/4);
                    this.dividend_data.put(third_quarter_dividend_date,yearly_dividend_normalized/4);
                    this.dividend_data.put(fourth_quarter_dividend_date,yearly_dividend_normalized/4);

                }
            } catch (FileNotFoundException e) {
                System.out.println("Dividends file was not found");
            }
        }

        if (Files.exists(Paths.get(treasury_rate_file_name))) {
            this.treasury_data = new LinkedHashMap<>();
            Scanner scanner;
            try {
                scanner = new Scanner(new File(pe_file_name));
                scanner.nextLine();
                while (scanner.hasNext()) {
                    String line = scanner.nextLine();

                    List<String> line_list = new ArrayList<>(Arrays.asList(line.split(",")));

                    //Extracting proper date
                    //Sample =  03/01/1950,16.66
                    List<String> date_split = new ArrayList<>(Arrays.asList(line_list.get(0).split("/")));
                    String sb = date_split.get(2)+"-" +date_split.get(1)+"-"+date_split.get(0);
                    this.treasury_data.put(sb, Double.parseDouble(line_list.get(1)));

                }
            } catch (FileNotFoundException e) {
                System.out.println("P/E file was not found");
            }
        }

        if (Files.exists(Paths.get(pe_file_name))) {
            this.pe_data = new LinkedHashMap<>();
            Scanner scanner;
            try {
                scanner = new Scanner(new File(pe_file_name));
                scanner.nextLine();
                while (scanner.hasNext()) {
                    String line = scanner.nextLine();

                    List<String> line_list = new ArrayList<>(Arrays.asList(line.split(",")));

                    //Extracting proper date
                    //Sample =  03/01/1950,16.66
                    List<String> date_split = new ArrayList<>(Arrays.asList(line_list.get(0).split("/")));
                    String sb = date_split.get(2)+"-" +date_split.get(1)+"-"+date_split.get(0);
                    this.pe_data.put(sb, Double.parseDouble(line_list.get(1)));

                }
            } catch (FileNotFoundException e) {
                System.out.println("P/E file was not found");
            }
        }

        if (Files.exists(Paths.get(unemployment_file_name))) {
            this.unemployment_data = new LinkedHashMap<>();
            Scanner scanner;
            try {
                scanner = new Scanner(new File(unemployment_file_name));
                scanner.nextLine();
                while (scanner.hasNext()) {
                    String line = scanner.nextLine();

                    List<String> line_list = new ArrayList<>(Arrays.asList(line.split(",")));

                    //Extracting proper date
                    //Sample =  03/01/1950,16.66
                    List<String> date_split = new ArrayList<>(Arrays.asList(line_list.get(0).split("/")));
                    String sb = date_split.get(2) + "-" + date_split.get(1) + "-" + date_split.get(0);
                    this.unemployment_data.put(sb, Double.parseDouble(line_list.get(1)));//value is in percent
                }

            } catch (FileNotFoundException e) {
                System.out.println("Unemployment file was not found");
            }
        }


        }

    private Double from_feb2018_to_right_historic_value(String current_date, double feb_2018_yearly_div) {
        //cumulative inflation rate

        int start_year = Integer.parseInt(current_date.split("-")[0]);
        int end_year = 2018; //TODO: it can be changed in the future

        List<Double> inflation_values =  inflation_data.entrySet().parallelStream()
                .filter(key -> (Integer.valueOf(key.getKey().split("-")[0]) >= start_year && Integer.valueOf(key.getKey().split("-")[0]) <= end_year))
                .map(Map.Entry::getValue).collect(Collectors.toList());

        //calculate pv from future value

        double multiplier = 1.0;
        //TODO: implement lambda
        for (Double inflation : inflation_values){
            multiplier = multiplier*(1+inflation);
        }
        //return value
        return  feb_2018_yearly_div/multiplier;

    }

    //Helper
    private String dividend_weekend_to_monday(String date){
        if (stock_data.keySet().contains(date)){
            return date;
        }
        return dividend_weekend_to_monday(LocalDate.parse(date).plusDays(1).toString());
    }

    public double get_stock_value(String current_date,String type){

        switch (type){
            case ("Open"):
                if (stock_data.containsKey(current_date)){
                    return stock_data.get(current_date);
                }
                //If we don't have data for today, I'll have to return yesterdays data
                current_date = LocalDate.parse(current_date).plusDays(-1).toString();
                return get_stock_value(current_date,type);
            case ("High"):
                if (high_data.containsKey(current_date)){
                    return high_data.get(current_date);
                }
                //If we don't have data for today, I'll have to return yesterdays data
                current_date = LocalDate.parse(current_date).plusDays(-1).toString();
                return get_stock_value(current_date,type);
            case ("Low"):
                if (low_data.containsKey(current_date)){
                    return low_data.get(current_date);
                }
                //If we don't have data for today, I'll have to return yesterdays data
                current_date = LocalDate.parse(current_date).plusDays(-1).toString();
                return get_stock_value(current_date,type);
        }
        System.out.println("Type: ("+type+") not found in database");
        return Double.parseDouble(null);

        }

    public double get_pe_value(String current_date) {
        if (pe_data.containsKey(current_date)){
            return pe_data.get(current_date);
        }
        //If we have data for 01-01-1950 and 01-02-1950, then data for 02-02-1950 is the same as for 01-01-2950
        current_date = LocalDate.parse(current_date).plusDays(-1).toString();
        return get_pe_value(current_date);
    }
    public double get_vix_value(String current_date) {
        if (vix_data.containsKey(current_date)){
            return vix_data.get(current_date);
        }
        //If we have data for 01-01-1950 and 01-02-1950, then data for 02-02-1950 is the same as for 01-01-2950
        current_date = LocalDate.parse(current_date).plusDays(-1).toString();
        return get_vix_value(current_date);
    }




    public double get_last_dividend_value(String current_date) {
        //used in  BSM option pricing
        if (dividend_data.containsKey(current_date)){
            return dividend_data.get(current_date);
        }

        current_date = LocalDate.parse(current_date).plusDays(-1).toString();
        return get_last_dividend_value(current_date);
    }



    public double get_unemployment_value(String current_date) {
        if (unemployment_data.containsKey(current_date)){
            return unemployment_data.get(current_date);
        }
        //If we have data for 01-01-1950 and 01-02-1950, then data for 02-02-1950 is the same as for 01-01-2950
        current_date = LocalDate.parse(current_date).plusDays(-1).toString();
        return get_unemployment_value(current_date);
    }

    public double get_treasury_value(String signing_date) {
        if (treasury_data.containsKey(signing_date)){
            return treasury_data.get(signing_date);
        }
        //If we have data for 01-01-1950 and 01-02-1950, then data for 02-02-1950 is the same as for 01-01-2950
        signing_date = LocalDate.parse(signing_date).plusDays(-1).toString();
        return get_treasury_value(signing_date);

    }

    public double get_dividend_payment(String signing_date) {
        if (dividend_data.containsKey(signing_date)){
            return dividend_data.get(signing_date);
        }
        return 0;
    }

    public boolean contains_day (String day){
        return stock_data.keySet().contains(day);
    }


    //Helper function
    private double date_dif(String start_date, String end_date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date date1,date2;
        try {
            date1 = sdf.parse(start_date);
            date2 = sdf.parse(end_date);
            return abs(date2.getTime() - date1.getTime()) / (86400000);
        } catch (ParseException e) {
            System.out.println("Signing date and exercise date are not properly formatted.");
        }
        return Double.parseDouble(null);
    }

    //STRATEGY

    public double past_x_days_volatility_annualised (int number_of_days,String today,String type){
        String start_date = LocalDate.parse(today).plusDays(-number_of_days).toString();
        Double[] values = new Double[number_of_days+1];
        int i = 0;
        switch (type){
            case ("Stock"):
                while (i < values.length){
                    values[i] = this.get_stock_value(start_date,"Open");
                    start_date = LocalDate.parse(start_date).plusDays(1).toString();
                    i++;
                }
                break;
            case ("P/E"):
                //we don't have daily data, we have to use monthly data
                while (i < values.length){
                    values[i] = this.get_pe_value(start_date);
                    start_date = LocalDate.parse(start_date).plusDays(1).toString();
                    i++;
                }
                break;
            case ("Unemployment"):
                //we don't have daily data, we have to use monthly data

                while (i < values.length){
                    values[i] = this.get_unemployment_value(start_date);
                    start_date = LocalDate.parse(start_date).plusDays(1).toString();
                    i++;
                }
                break;

            default:
                    System.out.println("Error calculating value. No type("+type+") found.");
                    return Double.parseDouble(null);
        }
        double mean = Arrays.asList(values).parallelStream().mapToDouble(p -> p).sum()/values.length;
        double sq_sum = Arrays.stream(values).parallel().mapToDouble(p -> p*p).sum();
        double variance = sq_sum / values.length - Math.pow(mean,2);

        //Annualized
        return sqrt(variance)*sqrt(365.0/number_of_days);
    }


    public double average_value(String start_date,String end_date,String type) {
        int i = 0;
        int date_diff = new Double(date_dif(start_date,end_date)).intValue();
        Double[] values = new Double[date_diff];
        switch (type){
            case ("P/E"):
                while (i < date_diff && !start_date.equals(end_date)){
                    values[i] = this.get_pe_value(start_date);
                    start_date = LocalDate.parse(start_date).plusDays(1).toString();
                    i++;
                }
                break;
            case("Unemployment"):
                while (i < date_diff && !start_date.equals(end_date)){
                   values[i] = this.get_unemployment_value(start_date);
                   start_date = LocalDate.parse(start_date).plusDays(1).toString();
                   i++;
               }
                break;
            case ("simple_moving_average"):
                while (i < date_diff && !start_date.equals(end_date)){
                  values[i] = this.get_stock_value(start_date,"Open");
                  start_date = LocalDate.parse(start_date).plusDays(1).toString();
                 i++;
                }
                break;

            default:
                System.out.println("Error: type("+type+") can't be used for average.");
                return i;
        }
        return Arrays.asList(values).parallelStream().mapToDouble(p -> p).sum()/values.length;

    }


    public double RSI(String current_date, int time_period) {
        //TODO: lambda
        //starting date for data collection
        current_date = LocalDate.parse(current_date).plusDays(-time_period-1).toString();

        //staring from older to newer
        Double[] prices = new Double[time_period+1];
        int i = 0;
        while (i <= time_period){
            prices[i] = get_stock_value(current_date,"Open");
            current_date = LocalDate.parse(current_date).plusDays(+1).toString();
            i++;
        }
        //Source: http://www.javased.com/index.php?source_dir=trademaker/src/org/lifeform/chart/indicator/RSI.java

        double gains = 0, losses = 0;

        for (int bar = 1; bar < prices.length; bar++) {
            gains += Math.max(0, prices[bar]- prices[bar-1]); //Math.max(0,change)
            losses += Math.max(0, -(prices[bar]- prices[bar-1])); // /Math.max(0,-change)
        }


        return (gains + losses == 0) ? 50 : (100 * gains / (gains + losses)); //gains+losses = change

    }

    public void record_order(String order_text) {
        try
        {
            FileWriter fw = new FileWriter(log_file_name,true); //the true will append the new data
            fw.write(order_text+"\n");//appends the string to the file
            fw.close();
        }
        catch(IOException ioe)
        {
            System.err.println("IOException: " + ioe.getMessage());
        }

    }


    public void record_signal(String signal_text) {
        try
        {
            FileWriter fw = new FileWriter(signal_file_name,true); //the true will append the new data
            fw.write(signal_text+"\n");//appends the string to the file
            fw.close();
        }
        catch(IOException ioe)
        {
            System.err.println("IOException: " + ioe.getMessage());
        }
    }

    public int suitable_days(String current_date, int decided_day_amount) {
      //  If I want to buy option in 90 days, but in 90 days's it's Sunday then I'll have to buy on Monday. This function return 91 instead of 90.
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        //TODO: implement more general solution
        Date date;
        try {
            date = sdf.parse(current_date);

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.add(Calendar.DATE,decided_day_amount);

            int i = 0;

            while (true){
                if(trading_time.contains(calendar.getTimeInMillis())){
                    break;
                }
                //the stock exchange is open at least once a week
                else if(i>7){
                    break;
                }
                else{
                    calendar.add(Calendar.DATE,1);
                    i++;
                }
            }

            return decided_day_amount+i;

        } catch (ParseException e) {
            System.out.println("Error calculating suitable days");
            return 0;
        }

    }

    public String get_order_action_by_id (String id){
        return order_id_action.get(id);
    }


    public void case1_signal(String message) {
        try(FileWriter fw = new FileWriter("/Users/Gustav/IdeaProjects/S_P500_trading_optionss/src/Data/case1_signals", true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw))
        {
            out.println(message);
        } catch (IOException e) {
            System.out.println("Error: did not find the buy signals file.");
        }
    }
    public void case2_signal(String message) {
        try(FileWriter fw = new FileWriter("/Users/Gustav/IdeaProjects/S_P500_trading_optionss/src/Data/case2_signals", true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw))
        {
            out.println(message);
        } catch (IOException e) {
            System.out.println("Error: did not find the sell signals file.");
        }
    }
}

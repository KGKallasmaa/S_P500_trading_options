import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;


public class Strategy {

    private Portfolio portfolio;
    private Database database;
    private boolean use_trading_fees;

    Strategy(Portfolio p,Database db,boolean trading_fees){
        this.portfolio = p;
        this.database = db;
        this.use_trading_fees = trading_fees;
    }
    //Helper function
    public void setPortfolio(Portfolio p){
        this.portfolio = p;
    }

    public boolean isUse_trading_fees() {return use_trading_fees;}
    //Different strategies

    //1. RSI
    //TODO fix reporting
    private synchronized void trade_RSI(String current_date) {
        int time_period = 14; //days
        double current_price = database.get_stock_value(current_date,"Open");

        double rsi = database.RSI(current_date,time_period);
        //RULE 1: Is the RSI < 30
        if (rsi <= 30.0) {
            String yesterday = LocalDate.parse(current_date).plusDays(-1).toString();
            //1.1 Was RULE 1 also true yesterday?
            double yesterday_rsi = database.RSI(yesterday, time_period);
            if (yesterday_rsi > 30) {
                //Reporting
                String order_id_1 = UUID.randomUUID().toString();
                String order_id_2 = UUID.randomUUID().toString();
                int number_of_days = database.suitable_days (current_date,90);

                // 1 set = buy 100 shares and 1 call option

                int number_of_sets = quantity_manager(current_date,"RSI","<=30",order_id_1);
                if (number_of_sets > 0){
                    Stock stock = new Stock(order_id_1,"SPY", database, 100);
                    String obligation_date = LocalDate.parse(current_date).plusDays(number_of_days).toString();
                    double volatility = database.past_x_days_volatility_annualised(number_of_days, current_date,"Stock");

                    //Reporting
                    int number_of_stocks_before = (portfolio.getStocks().isEmpty())? 0: portfolio.getStocks().get(0).get_Quantity();
                    int number_of_options_before = portfolio.getOptions().size();

                    Option option = new Option(order_id_2,database,"Call","Buy",current_price,current_date,obligation_date,volatility,current_price,100);


                    List<Double> stock_fee_list = new ArrayList<>();
                    List<Double> option_fee_list = new ArrayList<>();


                    IntStream.range(0, number_of_sets).forEach($ ->
                    {
                        stock_fee_list.add(this.portfolio.buy_stock(order_id_1, "SPY", 100, current_price, current_date,"Initial",use_trading_fees));
                        this.portfolio.add_stock_obligation(obligation_date, "Sell", stock);
                        option_fee_list.add(this.portfolio.buy_option(order_id_2,"Call",current_price, current_date, obligation_date, volatility, 100,use_trading_fees));
                    });

                    int number_of_stocks_after = portfolio.getStocks().get(0).get_Quantity();
                    int number_of_options_after = portfolio.getOptions().size();

                    Double number_of_actual_stock_sets = Math.abs((number_of_stocks_after)-(number_of_stocks_before))/100.0;
                    int number_of_actual_option_sets = (number_of_options_after-number_of_options_before);

                    double stock_fee = stock_fee_list.stream().mapToDouble(p->p).sum();
                    double option_fee = option_fee_list.stream().mapToDouble(p->p).sum();


                    //Reporter
                    Order order1 = new Order(order_id_1,current_date,"Stock","Buy","RSI","Buy 100 shares",number_of_actual_stock_sets.intValue(),-current_price*100*number_of_actual_stock_sets-stock_fee);
                    //later we'll subtract the market price from order1
                    Order order2 = new Order(order_id_2,current_date,"Option","Buy","RSI","Buy 1 call option",number_of_actual_option_sets,-option.option_premium_BS()*number_of_actual_option_sets*100-option_fee);


                    portfolio.add_order(order1);
                    portfolio.add_order(order2);

                    //Statistics
                    //TODO fix
                    database.case1_signal(current_date+": RSI");
                }

            }
        }
        //RULE 2: Is the RSI > 70
        else if (rsi >= 70){
            String yesterday = LocalDate.parse(current_date).plusDays(-1).toString();
            //1.1 Was RULE 1 also true yesterday?
            double yesterday_rsi = database.RSI(yesterday,time_period);
            if (yesterday_rsi < 70) {
                //Reporting
                String order_id_1 = UUID.randomUUID().toString();
                String order_id_2 = UUID.randomUUID().toString();
                int number_of_days = database.suitable_days(current_date, 90);


                //  1 set = sell 100 shares and 1 put option
                int number_of_sets = quantity_manager(current_date, "RSI", ">=70", order_id_2);
                if (number_of_sets > 0) {
                    Stock stock = new Stock(order_id_1, "SPY", database, 100);
                    String obligation_date = LocalDate.parse(current_date).plusDays(number_of_days).toString();
                    double volatility = database.past_x_days_volatility_annualised(number_of_days, current_date, "Stock");

                    //Reporting
                    int number_of_stocks_before = (portfolio.getStocks().isEmpty())? 0: portfolio.getStocks().get(0).get_Quantity();
                    int number_of_options_before = portfolio.getOptions().size();

                    Option option = new Option(order_id_2, database, "Put", "Sell", current_price, current_date, obligation_date, volatility, current_price, 100);


                    List<Double> stock_fee_list = new ArrayList<>();
                    List<Double> option_fee_list = new ArrayList<>();


                    IntStream.range(0, number_of_sets).forEach($ ->
                    {
                        stock_fee_list.add(this.portfolio.sell_stock(order_id_1, "SPY", 100, current_price, current_date,"Initial",use_trading_fees));
                        this.portfolio.add_stock_obligation(obligation_date, "Buy", stock);
                        option_fee_list.add(this.portfolio.sell_option(order_id_2, "Put", current_price, current_date, obligation_date, volatility, 100,use_trading_fees));
                    });


                    int number_of_stocks_after = portfolio.getStocks().get(0).get_Quantity();
                    int number_of_options_after = portfolio.getOptions().size();
                    Double number_of_actual_stock_sets = Math.abs((number_of_stocks_after)-(number_of_stocks_before))/100.0;
                    int number_of_actual_option_sets = (number_of_options_after-number_of_options_before);

                    double stock_fee = stock_fee_list.stream().mapToDouble(p->p).sum();
                    double option_fee = option_fee_list.stream().mapToDouble(p->p).sum();


                    List<Asset> usless_asset_list = new ArrayList<>();
                    double short_sell_interest_payment = portfolio.interest_payment(current_date,obligation_date,usless_asset_list,"Short");

                    IntStream.range(0,number_of_actual_stock_sets.intValue()).forEach($ -> this.portfolio.add_interest_obligation(order_id_1,obligation_date,short_sell_interest_payment));


                    //Reporter
                    Order order1 = new Order(order_id_1, current_date, "Stock", "Sell", "RSI", "Sell 100 shares", number_of_actual_stock_sets.intValue(), 100 * current_price * number_of_actual_stock_sets-stock_fee);
                    //later we'll subtract the market price from order1
                    Order order2 = new Order(order_id_2, current_date, "Option", "Sell", "RSI", "Sell 1 put option", number_of_actual_option_sets, +option.option_premium_BS() * number_of_actual_option_sets*100-option_fee);

                    portfolio.add_order(order1);
                    portfolio.add_order(order2);

                    //Statistics
                    database.case2_signal(current_date + ": RSI");
                }
            }
        }
    }


    //2. Simple moving average:
    //TODO fix reporting
    private synchronized Portfolio trade_simple_moving_average(String current_date) {
        //Base information
        double current_price = database.get_stock_value(current_date,"Open");
        String start_date_50 = LocalDate.parse(current_date).plusDays(-50).toString();
        String start_date_200 = LocalDate.parse(current_date).plusDays(-200).toString();
        String yesterday = LocalDate.parse(current_date).plusDays(-1).toString();
        double moving_average_50 = database.average_value(start_date_50,current_date,"simple_moving_average");
        double moving_average_200 = database.average_value(start_date_200,current_date,"simple_moving_average");

        //RULE 1: is the current 50_day_moving average bigger than 200_day moving_average
        if (moving_average_50 >= moving_average_200){
            //Reporting
            String order_id_1 = UUID.randomUUID().toString();
            start_date_50 = LocalDate.parse(yesterday).plusDays(-50).toString();
            start_date_200 = LocalDate.parse(yesterday).plusDays(-200).toString();
            double moving_50_yesterday = database.average_value(start_date_50,yesterday,"simple_moving_average");
            double moving_200_yesterday = database.average_value(start_date_200,yesterday,"simple_moving_average");

            //RULE 2: Was Rule1 not true yesterday?
            if (moving_50_yesterday <= moving_200_yesterday) {
                // 1 set = buy 3 call
                int number_of_sets = quantity_manager(current_date, "Moving Average", "50 >= 200", order_id_1);
                if (number_of_sets > 0) {
                    //General information
                    int number_of_days = database.suitable_days(current_date, 90);
                    String obligation_date = LocalDate.parse(current_date).plusDays(number_of_days).toString();
                    double volatility = database.past_x_days_volatility_annualised(number_of_days, current_date, "Stock");
                    List<Double> option_fee_list = new ArrayList<>();
                    //Reporting
                    int number_of_options_before = portfolio.getOptions().size();
                    Option option = new Option(order_id_1, database, "Call", "Buy", current_price, current_date, obligation_date, volatility, current_price, 100);
                    //Order options
                    IntStream.range(0, number_of_sets).forEach($ ->
                    {
                        option_fee_list.add(this.portfolio.buy_option(order_id_1, "Call", current_price, current_date, obligation_date, volatility, 100,use_trading_fees));
                        option_fee_list.add(this.portfolio.buy_option(order_id_1, "Call", current_price, current_date, obligation_date, volatility, 100,use_trading_fees));
                        option_fee_list.add(this.portfolio.buy_option(order_id_1, "Call", current_price, current_date, obligation_date, volatility, 100,use_trading_fees));
                    });
                    //Order analysis
                    double option_fee = option_fee_list.stream().mapToDouble(p->p).sum();
                    int number_of_options_after = portfolio.getOptions().size();
                    int number_of_actual_option_sets = (number_of_options_after - number_of_options_before);
                    //Reporter
                    Order order1 = new Order(order_id_1, current_date, "Option", "Buy", "MAVG", "Buy 3 call options", number_of_actual_option_sets, - option.option_premium_BS() * number_of_actual_option_sets*100-option_fee);
                    portfolio.add_order(order1);
                    //Statistics
                    database.case1_signal(current_date + ": Moving_avg");
                }
            }
        }
        //RULE 2: is the current 50_day_moving average smaller than 200_day moving_average
        else  {
            //RULE 2: Was it true at time (t-1)
            start_date_50 = LocalDate.parse(yesterday).plusDays(-50).toString();
            start_date_200 = LocalDate.parse(yesterday).plusDays(-200).toString();
            double moving_50_yesterday = database.average_value(start_date_50,yesterday,"simple_moving_average");
            double moving_200_yesterday = database.average_value(start_date_200,yesterday,"simple_moving_average");

            if (moving_50_yesterday >= moving_200_yesterday) {
                //Reporting
                String order_id_1 = UUID.randomUUID().toString();
                int number_of_days = database.suitable_days(current_date, 90);

                // 1 set = sell 2 call
                int number_of_sets = quantity_manager(current_date, "Moving Average", "200 > 50", order_id_1);
                if (number_of_sets > 0) {
                    String obligation_date = LocalDate.parse(current_date).plusDays(number_of_days).toString();
                    double volatility = database.past_x_days_volatility_annualised(number_of_days, current_date, "Stock");

                    //Reporting
                    int number_of_options_before = portfolio.getOptions().size();
                    Option option = new Option(order_id_1, database, "Call", "Sell", current_price, current_date, obligation_date, volatility, current_price, 100);


                    List<Double> option_fee_list = new ArrayList<>();


                    IntStream.range(0, number_of_sets).forEach($ ->
                    {
                        option_fee_list.add(this.portfolio.sell_option(order_id_1, "Call", current_price, current_date, obligation_date, volatility, 100,use_trading_fees));
                        option_fee_list.add(this.portfolio.sell_option(order_id_1, "Call", current_price, current_date, obligation_date, volatility, 100,use_trading_fees));
                    });

                    int number_of_options_after = portfolio.getOptions().size();
                    int number_of_actual_option_sets = (number_of_options_after - number_of_options_before);

                    double option_fee = option_fee_list.stream().mapToDouble(p->p).sum();

                    //Reporter
                    Order order1 = new Order(order_id_1, current_date, "Option", "Sell", "MAVG", "Sell 2 call options", number_of_actual_option_sets,  option.option_premium_BS() * number_of_actual_option_sets*100-option_fee);


                    portfolio.add_order(order1);

                    //Statistics
                    database.case2_signal(current_date + ": Moving_avg");
                }
            }
        }
        return portfolio;
    }


    //3. P/E
    //TODO fix reporting
    private synchronized Portfolio trade_PE(String current_date) {
        String starting_date = LocalDate.parse(current_date).plusDays(-365*3).toString();

        double current_price = database.get_stock_value(current_date,"Open");
        double today_pe = database.get_pe_value(current_date);
        double pe_3year_average = database.average_value(starting_date,current_date,"P/E");
        String yesterday = LocalDate.parse(current_date).plusDays(-1).toString();

        //Rule 1: is the currents month's P/e bigger than (sdev+3_year_average)?
        if(today_pe >= pe_3year_average){
            //It's important that the signal was not present yesterday

            double yesterday_pe = database.get_pe_value(yesterday);
            String starting_date_for_yesterday = LocalDate.parse(yesterday).plusDays(-365*3).toString();
            double pe_3year_average_yesterday = database.average_value(starting_date_for_yesterday,yesterday,"P/E");

            if(yesterday_pe < pe_3year_average_yesterday) {
                //Reporting
                String order_id_1 = UUID.randomUUID().toString();

                // 1 set = sell 1 put
                int number_of_sets = quantity_manager(current_date, "PE", "Sell", order_id_1);
                if (number_of_sets > 0) {
                    int number_of_days = database.suitable_days(current_date, 90);
                    String obligation_date = LocalDate.parse(current_date).plusDays(number_of_days).toString();
                    double volatility = database.past_x_days_volatility_annualised(number_of_days, current_date, "Stock");

                    //Reporting
                    int number_of_options_before = portfolio.getOptions().size();
                    Option option = new Option(order_id_1, database, "Put", "Sell", current_price, current_date, obligation_date, volatility, current_price, 100);

                    List<Double> option_fee_list = new ArrayList<>();


                    IntStream.range(0, number_of_sets).forEach((int $) -> option_fee_list.add(this.portfolio.sell_option(order_id_1, "Put", current_price, current_date, obligation_date, volatility, 100,use_trading_fees)));

                    int number_of_options_after = portfolio.getOptions().size();
                    int number_of_actual_option_sets = (number_of_options_after - number_of_options_before);

                    double option_fee = option_fee_list.stream().mapToDouble(p->p).sum();

                    //Reporter
                    Order order1 = new Order(order_id_1, current_date, "Option", "Sell", "PE", "Sell 1 put option", number_of_actual_option_sets, option.option_premium_BS() * number_of_actual_option_sets*100-option_fee);
                    portfolio.add_order(order1);


                    //Statistics
                    database.case1_signal(current_date + ": PE");
                }
            }
        }
        //Rule 2: is the currents months P/e smaller than (3_year average)?
        else {
            //It's important that the signal was not present yesterday
            double yesterday_pe = database.get_pe_value(yesterday);
            String starting_date_for_yesterday = LocalDate.parse(yesterday).plusDays(-365 * 3).toString();
            double pe_3year_average_yesterday = database.average_value(starting_date_for_yesterday, yesterday, "P/E");

            if (yesterday_pe > (pe_3year_average_yesterday)) {
                //Reporting
                String order_id_1 = UUID.randomUUID().toString();

                // 1 set = buy 1 call
                int number_of_sets = quantity_manager(current_date, "PE", "Buy", order_id_1);
                if (number_of_sets > 0) {

                    int number_of_days = database.suitable_days(current_date, 90);
                    String obligation_date = LocalDate.parse(current_date).plusDays(number_of_days).toString();
                    double volatility = database.past_x_days_volatility_annualised(number_of_days, current_date, "Stock");

                    //Reporting
                    int number_of_options_before = portfolio.getOptions().size();
                    Option option = new Option(order_id_1, database, "Call", "Buy", current_price, current_date, obligation_date, volatility, current_price, 100);


                    List<Double> option_fee_list = new ArrayList<>();

                    IntStream.range(0, number_of_sets).forEach($ -> option_fee_list.add(this.portfolio.buy_option(order_id_1, "Call", current_price, current_date, obligation_date, volatility, 100,use_trading_fees)));

                    int number_of_options_after = portfolio.getOptions().size();
                    int number_of_actual_option_sets = (number_of_options_after - number_of_options_before);

                    double option_fee = option_fee_list.stream().mapToDouble(p->p).sum();

                    //Reporter
                    Order order1 = new Order(order_id_1, current_date, "Option", "Sell", "PE", "Buy 1 call option", number_of_actual_option_sets, -option.option_premium_BS() * number_of_actual_option_sets*100-option_fee);
                    portfolio.add_order(order1);

                    //Statistics
                    database.case2_signal(current_date + ": PE");
                }
            }
        }
        return portfolio;
    }

    //4. Unemployment
    //TODO fix reporting
    private synchronized Portfolio trade_Unemployment(String current_date) {
        String starting_date = LocalDate.parse(current_date).plusDays(-365 * 3).toString();
        String yesterday = LocalDate.parse(current_date).plusDays(-1).toString();
        double market_price = database.get_stock_value(current_date, "Open");
        double unemployment_rate_today = database.get_unemployment_value(current_date);
        double average_unemployment_value = database.average_value(starting_date, current_date, "Unemployment");


        String old_starting_date = LocalDate.parse(starting_date).plusDays(-1).toString();
        double unemployment_rate_yesterday = database.get_unemployment_value(yesterday);
        double average_unemployment_yesterday = database.average_value(old_starting_date, yesterday, "Unemployment");


        boolean trading_ok_today = (unemployment_rate_today <= average_unemployment_value) && (unemployment_rate_yesterday >= average_unemployment_yesterday) || ((unemployment_rate_today >= average_unemployment_value) && (unemployment_rate_yesterday <= average_unemployment_yesterday));
        if (trading_ok_today) {
            int number_of_days = database.suitable_days(current_date, 90);
            //Reporting
            String order_id_1 = UUID.randomUUID().toString();
            String order_id_2 = UUID.randomUUID().toString();

            // Set = Sell 1 put option, buy 1 call option
            int number_of_sets = quantity_manager(current_date, "Unemployment", "", order_id_1);
            if (number_of_sets > 0) {

                String obligation_date = LocalDate.parse(current_date).plusDays(number_of_days).toString();
                double volatility = database.past_x_days_volatility_annualised(number_of_days, current_date, "Stock");

                //Reporting
                int number_of_options_before = portfolio.getOptions().size();
                Option put_option = new Option(order_id_1, database, "Put", "Sell", market_price, current_date, obligation_date, volatility, market_price, 100);
                Option call_option = new Option(order_id_1, database, "Call", "Buy", market_price, current_date, obligation_date, volatility, market_price, 100);


                List<Double> option_fee_list = new ArrayList<>();

                IntStream.range(0, number_of_sets).parallel().forEach($ -> {
                    option_fee_list.add(this.portfolio.sell_option(order_id_1, "Put", market_price, current_date, obligation_date, volatility, 100,use_trading_fees));
                    option_fee_list.add(this.portfolio.buy_option(order_id_1, "Call", market_price, current_date, obligation_date, volatility, 100,use_trading_fees));
                });

                int number_of_options_after = portfolio.getOptions().size();
                int number_of_actual_option_sets = (number_of_options_after - number_of_options_before);

                double option_fee = option_fee_list.stream().mapToDouble(p->p).sum();

                //Reporter
                Order order1 = new Order(order_id_1, current_date, "Option", "Buy", "Unemployment", "Buy 1 call option", number_of_actual_option_sets / 2, -100*call_option.option_premium_BS() * number_of_actual_option_sets/2.0-option_fee/2.0);
                Order order2 = new Order(order_id_2, current_date, "Option", "Sell", "Unemployment", "Sell 1 put option", number_of_actual_option_sets / 2,100*put_option.option_premium_BS() * number_of_actual_option_sets/2.0-option_fee/2.0);
                portfolio.add_order(order1);
                portfolio.add_order(order2);

                //Statistics
                database.case1_signal(current_date + ": Unemployment");
            }
        }
        return portfolio;
    }


    public Portfolio trade_all(String current_date){
        trade_Unemployment(current_date); // todo: profitable (1.113814)  time = 5 minutes
        trade_PE(current_date); // todo: profitable (1.444446223) time =  5 minutes
        trade_simple_moving_average(current_date); // //todo: profitable (16.2895996800) time = 0 minutes
        trade_RSI(current_date); // todo: profitable (1157.911798) time =   3 minute

        //TODO: overall strategy is  profitable (696.647), total time = 13 minutes

        return portfolio;
    }

    private int quantity_manager(String today, String type, String subtype, String order_id){

        double market_price = database.get_stock_value(today,"Open");

        BigDecimal money_available = portfolio.free_money(today).multiply(new BigDecimal(1));

        //Checking if there's any money available
        if (money_available.compareTo(BigDecimal.ZERO) < 0){
            return 0;
        }

        int number_of_days = database.suitable_days (today,90);
        String obligation_date = LocalDate.parse(today).plusDays(number_of_days).toString();
        double volatility = database.past_x_days_volatility_annualised(number_of_days,today,"Stock");
        double value_of_one_set = 0;

        switch (type){
            case ("RSI"):
                switch (subtype){
                    case ("<=30"):
                       // Original idea: Buy 100 shares, buy 1 call option
                        Option option = new Option(order_id,database,"Call","Buy",market_price,today,obligation_date,volatility,market_price,100);
                        value_of_one_set = 100*(market_price) +100*(option.option_premium_BS()+market_price); //(stock + option)
                        break;
                    case (">=70"):
                        // Original idea: Sell 100 shares, sell 1 put option
                        option = new Option(order_id,database,"Put","Sell",market_price,today,obligation_date,volatility,market_price,100);
                        value_of_one_set = 100*(market_price)+100*(market_price-option.option_premium_BS()); //(stock + option)
                        break;
                }
                break;
            case ("PE"):
                switch (subtype) {
                    case ("Sell"):
                        Option option = new Option(order_id,database, "Put", "Sell", market_price, today, obligation_date, volatility, market_price, 100);
                        // Original idea:  Sell 1 put
                        value_of_one_set = 100*(market_price-option.option_premium_BS()); //(option)
                        break;

                    case ("Buy"):
                        option = new Option(order_id,database, "Call", "Buy", market_price, today, obligation_date, volatility, market_price, 100);
                        // Original idea:  Buy 1 call
                        value_of_one_set = 100 * (market_price+option.option_premium_BS()); //(option)
                        break;
                }
                break;
            case ("Unemployment"):
                // Original idea: Sell 1 put option, buy 1 call option
                Option put_option = new Option(order_id,database, "Put", "Buy", market_price, today, obligation_date, volatility, market_price, 100);
                Option call_option = new Option(order_id,database, "Call", "Buy", market_price, today, obligation_date, volatility, market_price, 100);

                value_of_one_set = 100 * (market_price-put_option.option_premium_BS() + call_option.option_premium_BS()); // put option + call option
                break;

            case ("Moving Average"):
                switch (subtype){
                    case ("50 >= 200"):
                        // Original idea:  Buy 3 call options, buy 2 put options
                        call_option = new Option(order_id,database, "Call", "Buy", market_price, today, obligation_date, volatility, market_price, 100);
                        value_of_one_set = 3*100*(call_option.option_premium_BS()+market_price); // put option + call option
                        break;

                    case ("200 > 50"):
                        // Buy 3 put options and sell 2 call options
                        call_option = new Option(order_id,database, "Call", "Sell", market_price, today, obligation_date, volatility, market_price, 100);
                        value_of_one_set = + 2*100*(market_price-call_option.option_premium_BS()); // put option + call option
                        break;
                }
                break;

            default:
                System.out.println("Error: no type("+type+") found");
                return Integer.parseInt(null);
        }
        if (value_of_one_set == 0){
            return 100;
        }
        //number of sets rounded, max 100
        return Math.min(100,money_available.divide(new BigDecimal(Math.abs(value_of_one_set)), RoundingMode.HALF_DOWN).intValue());

    }
}

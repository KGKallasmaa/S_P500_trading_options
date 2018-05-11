import java.time.LocalDate;


public class MAIN {
    private static Reporter trade(String starting_date, final String ending_date, Portfolio portfolio, Reporter reporter, String order_file_name,boolean include_trading_fees) {

        int final_time = 0; //last day must also be included
        Strategy strategy = new Strategy(portfolio, portfolio.getDatabase(),include_trading_fees);
        Long starting_time = System.currentTimeMillis();


        System.out.println("Trading simulation starts: ");
        System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxx");

        while (final_time == 0) {
            //TECHNICAL
            if (starting_date.equals(ending_date)) {
                final_time++;
            }
            //Check if we have data about this day in the database? Maybe it's Christmas or maybe it's the weekend.
            if (portfolio.getDatabase().contains_day(starting_date)) {
                //1. Dividend payments
                portfolio.exercise_dividends(starting_date);

                //2. Do I have any rights or obligations (Options) / Obligations(Stocks)?
                portfolio.exercise_obligations(starting_date,strategy.isUse_trading_fees());

                //3. Strategy((1.)unemployment rate, (2.) P/E  (3) moving average (4) RSI)
                strategy.setPortfolio(strategy.trade_all(starting_date));

            }
            //TODO: remove the else part later
            else{
                portfolio.exercise_obligations(starting_date,strategy.isUse_trading_fees());
                portfolio.exercise_dividends(starting_date);
            }
            reporter.report(portfolio,starting_date);

            starting_date = LocalDate.parse(starting_date).plusDays(1).toString();

        }
        portfolio.order_results_to_file(order_file_name,ending_date);


        Long ending_time = System.currentTimeMillis();
        System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        System.out.println("Trading simulation ended: ");
        System.out.println("Simulation took " + Math.round((ending_time - starting_time) * 0.001 * 1 / 60) + " minutes to complete");

        return reporter;
    }



    public static void main(String[] args) {
        //Initial inputs
        int starting_cash = 1000000;
        Reporter reporter = new Reporter();
        boolean include_trading_fees = true;

        String base_url = "/Users/Gustav/IdeaProjects/S_P500_trading_optionss/src/Data/"; //change this



        String[] files = {base_url+"10_year_treasury_rate.csv",base_url+"pe_data.csv",base_url+"trading_signals_log",base_url+"unemployment_data.csv",base_url+"stock_data.csv",base_url+"order_log",
                base_url+"div_data.csv",base_url+"inflation_data.csv"};


        Database db = new Database(files[0],files[2],files[1],files[3],files[4],files[5],files[6],files[7]);
        String starting_date = "1953-12-03";
        String ending_date = "2018-03-23";
        Portfolio portfolio = new Portfolio(starting_cash,db);


        //Report file
        String order_file_name = "order_log.csv";

        //TRADE

        reporter = trade(starting_date,ending_date,portfolio,reporter,base_url+order_file_name,include_trading_fees);


        /*
        Print portfolio values
        for (String d : reporter.date_portfolio_value.keySet()){
          System.out.println(reporter.date_portfolio_value.get(d).divide(new BigDecimal(1000000)));
          }
         */


        //Report file
        String report_file_name = "results.csv";

        reporter.results_to_file(base_url+report_file_name);


    }



}

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class Portfolio {

    private List<Asset> port; //portfolio
    private Database database;
    private Queue<Stock> stock_obligation; // todo: it should be a priority que
    private Queue<Order> order_log;
    Portfolio(int money, Database db){
        this.port = new ArrayList<>();

        if (money >= 0){
            Cash cash = new Cash(money);
            port.add(cash);
        }
        this.database = db;
        this.stock_obligation = new LinkedList<>();
        this.order_log = new LinkedList<>();
    }


    public synchronized BigDecimal market_value(String current_date){
        BigDecimal total_market_value = new BigDecimal(port.parallelStream().mapToDouble(p->p.get_Asset_Value(current_date)).sum(), MathContext.DECIMAL64);
        BigDecimal stock_obligation_exposure = new BigDecimal(stock_obligation.parallelStream().mapToDouble(p->p.get_Asset_Value(current_date)).sum(), MathContext.DECIMAL64);
        return total_market_value.subtract(stock_obligation_exposure);
    }

    public synchronized BigDecimal value_without_options(String current_date){
        BigDecimal market_value_without_options = new BigDecimal(port.parallelStream().filter(asset->asset.getClass() != Option.class).mapToDouble(p->p.get_Asset_Value(current_date)).sum(), MathContext.DECIMAL64);
        BigDecimal stock_obligation_exposure = new BigDecimal(stock_obligation.stream().mapToDouble(p->p.get_Asset_Value(current_date)).sum(), MathContext.DECIMAL64);
        return market_value_without_options.subtract(stock_obligation_exposure);
    }

    private void add_asset(Asset asset){
        if (asset.getClass().equals(Stock.class)){add_stock((Stock) asset);}
        //Options
        else {port.add(asset);}
    }

    //Helper function
    private synchronized void add_money(double amount, String current_date){
        double current_cash = get_cash_available(current_date);
        double new_cash = current_cash+amount;

        if (new_cash < 0) {System.out.println("Error: Cash reserves are negative");}

        //remove old cash
        port.removeAll(port.parallelStream().filter(p->(p.getClass().equals(Cash.class))).collect(toList()));
        //add new cash
        if (new_cash != 0){
            port.add(new Cash(new_cash));
        }

    }
    private synchronized void add_stock(Stock stock){
        List<Asset> old_stocks = port.parallelStream().filter(p->(p.getClass().equals(Stock.class) && p.get_Name().equals(stock.get_Name()))).collect(toList());
        int old_quantity = old_stocks.stream().mapToInt(Asset::get_Quantity).sum();
        Stock new_stock = new Stock(stock.getOrder_id(),stock.get_Name(),database,old_quantity+stock.get_Quantity());
        port.removeAll(old_stocks);
        if (stock.get_Quantity() != 0){
            port.add(new_stock);
        }

    }
    public synchronized double get_cash_available(String current_date){return port.stream().filter(p->(p.getClass().equals(Cash.class))).mapToDouble(p->p.get_Asset_Value(current_date)).sum();}
    public synchronized List<Option> getOptions(){
        //TODO: implement lambda
        List<Option> options = new ArrayList<>();
         for (Asset a : port){
             if (a.getClass() == Option.class){
                 options.add((Option) a);
             }
         }
         return options;
    }
    public synchronized List<Stock> getStocks(){
        //TODO: implement lambda
        List<Stock> stocks = new ArrayList<>();
        for (Asset a : port){
            if (a.getClass() == Stock.class){
                stocks.add((Stock) a);
            }
        }
        return stocks;
    }
    public Database getDatabase() {return database;}
    public synchronized boolean add_stock_obligation(String obligation_date, String obligation_type, Stock stock){
        stock.setObligation(obligation_date,obligation_type);
        stock_obligation.add(stock);
        return true;
    }


    public synchronized boolean buy_stock(String order_id,String name,int quantity, double price,String current_date,String type){
        //type: used for order logging
        List<String> order_ids = order_log.stream().map(Order::getOrderID).collect(Collectors.toList());

        double money_to_be_taken = (quantity > 0 && price > 0) ? (quantity*price) : 0;
        if (money_to_be_taken == 0){ System.out.println("Error(buy stock): quantity or price negative"); return false;}

        if (money_to_be_taken < get_cash_available(current_date)){
            add_money(-money_to_be_taken,current_date);
            Stock stock = new Stock(order_id,name,database,quantity);
            add_asset(stock);

            if (type.equals("Initial") && order_ids.contains(order_id)){change_order_profit(order_id,"Stock",-money_to_be_taken);}
            else if (type.equals("Regular")){change_order_profit(order_id,"Stock",-money_to_be_taken);}

            return true;
        }
        System.out.println("Not enough capital to buy stock (money needed = "+money_to_be_taken+" money at hand"+get_cash_available(current_date));
        return false;
    }
    public synchronized boolean sell_stock(String order_id,String name,int quantity, double price,String current_date,String type){
        //type: used for order logging
        double money_to_be_added = (quantity > 0 && price > 0) ? (quantity*price) : 0;


        if (money_to_be_added == 0){
            System.out.println("Error(selling stock): quantity or price negative");
            return false;
        }

        add_money(money_to_be_added,current_date);
        Stock stock = new Stock(order_id,name,database,-quantity);
        add_asset(stock);

        List<String> order_ids = order_log.stream().map(Order::getOrderID).collect(Collectors.toList());


        if (type.equals("Initial") && order_ids.contains(order_id))
        { change_order_profit(order_id,"Stock",money_to_be_added);
        }
        else if (type.equals("Regular")){
            change_order_profit(order_id,"Stock",money_to_be_added);
        }

        return true;
    }

    public synchronized boolean buy_option(String order_id,String type,double strike_price,String signing_date,String exercise_date,double volatility,int number_of_stocks){
        double market_price = database.get_stock_value(signing_date,"Open");
        Option option = new Option(order_id,database,type,"Buy",strike_price,signing_date,exercise_date,volatility,market_price,number_of_stocks);

        //Do I have enough money to play the premium?
        if (get_cash_available(signing_date) >= option.option_premium_BS()*option.get_number_of_Stocks()){
            add_money(-option.option_premium_BS()*option.get_number_of_Stocks(),signing_date);
            add_asset(option);
            return true;
        }
        System.out.println("Error: not enough money for option premium payment. Money needed ("+(option.option_premium_BS()*option.get_number_of_Stocks())+") is bigger than the sum we have ("+get_cash_available(signing_date)+") ");
        return false;

    }
    public synchronized void sell_option(String order_id,String type,double strike_price,String signing_date,String exercise_date,double volatility,int number_of_stocks){
        double market_price = database.get_stock_value(signing_date,"Open");
        Option option = new Option(order_id,database,type,"Sell",strike_price,signing_date,exercise_date,volatility,market_price,number_of_stocks);
        add_money(option.option_premium_BS()*option.get_number_of_Stocks(),signing_date);
        add_asset(option);

    }
    private void exercise_option(Option option, String today) {


        double current_market_price = database.get_stock_value(today, "Open");
        double income = 0, expense = 0;

        //1. I have to exercise this option
        boolean i_must_exercise_this_option = option.get_Action().equals("Sell") && option.get_Asset_Value(today) >0;
        if (i_must_exercise_this_option) {
            //Call option
            if (option.get_Type().equals("Call")) {
                expense = option.get_Strike() * option.get_number_of_Stocks();
                income = current_market_price * option.get_number_of_Stocks();
            }
            //Put option
            else {
                income = option.get_Strike() * option.get_number_of_Stocks();
                expense = current_market_price * option.get_number_of_Stocks();
            }
            }
        //2. I want to exercise this option
        boolean i_want_exercise_this_option = option.get_Action().equals("Buy") && option.get_Asset_Value(today) > 0;

        if (i_want_exercise_this_option) {
            //Call option
            if (option.get_Type().equals("Call")) {
                expense = option.get_Strike() * option.get_number_of_Stocks();
                income = current_market_price * option.get_number_of_Stocks();
            }
            //Put option
            else {
                income = option.get_Strike() * option.get_number_of_Stocks();
                expense = current_market_price * option.get_number_of_Stocks();
                }
        }
        double net_income = income-expense;
        if (net_income != 0){
            change_order_profit(option.getOrder_id(),"Option",net_income);


            add_money(net_income, today);
        }

    }

    private void change_order_profit(String order_id,String type, double income) {
        double old_value= order_log.stream().mapToDouble(Order::getProfit).sum();

        List<Order> suitable_orders = order_log.stream().filter(
                order->order.getOrderID().equals(order_id) && Objects.equals(order.getOrder_Type(), type)
        ).collect(toList());

        //remove the old value
        order_log.removeAll(suitable_orders);
        //changing the money supply
        double income_per_order = income/(double)suitable_orders.size();


        if (income_per_order*suitable_orders.size() != income){
            System.out.println("Income should be: "+income+" income is: "+income_per_order*suitable_orders.size());
        }

        suitable_orders.forEach(order -> order.addMoney(income_per_order));
        //adding orders back to the log
        order_log.addAll(suitable_orders);

        double new_value = order_log.stream().mapToDouble(Order::getProfit).sum();
        if (Math.abs(new_value-income-old_value) > 1e-4){
            System.out.println("Error: Adding profit to order log. The value should be("+new_value+") but calculations only show ("+(old_value+income)+")"); //todo: add it back later
        }
    }


    public synchronized void exercise_obligations(String current_date){
        //1. Option obligation
        List<Asset> options_that_can_be_exercised = getOptions().parallelStream().filter(o-> o.can_be_exercised_today(current_date)).collect(toList());


        double interest_payment = (!options_that_can_be_exercised.isEmpty()) ? interest_payment(current_date,options_that_can_be_exercised) : 0;

        options_that_can_be_exercised.forEach(o->exercise_option((Option)o,current_date));
        port.removeAll(options_that_can_be_exercised);


        if (interest_payment != 0){
            //subtract interest payment
            add_money(-interest_payment,current_date);
            String order_id_1 = UUID.randomUUID().toString();
            Order order  = new Order(order_id_1,current_date,"Interest","Pay","RSI","Interest payment",1,-interest_payment);
            add_order(order);
        }

        //2. Stock obligations
        List<Stock> stocks_that_will_be_exercised_today = stock_obligation.parallelStream().filter(p->p.getObligation_date().equals(current_date)).collect(toList());
        List<Stock> stocks_that_will_be_sold_today = stocks_that_will_be_exercised_today.stream().filter(p->p.getObligation_type().equals("Sell")).collect(toList());
        List<Stock> stocks_that_will_be_bought_today = stocks_that_will_be_exercised_today.stream().filter(p->p.getObligation_type().equals("Buy")).collect(toList());
        stocks_that_will_be_sold_today.forEach(stock->sell_stock(stock.getOrder_id(),stock.get_Name(),stock.get_Quantity(),database.get_stock_value(current_date,"Open"),current_date,"Regular"));
        stocks_that_will_be_bought_today.forEach(stock->buy_stock(stock.getOrder_id(),stock.get_Name(),stock.get_Quantity(),database.get_stock_value(current_date,"Open"),current_date,"Regular"));
        //removing all obligations
        stock_obligation.removeAll(stocks_that_will_be_exercised_today);


    }
    public void exercise_dividends(String today) {
        //Do I have any dividend payment scheduled for today?
        double dividend_per_stock = database.get_dividend_payment(today);
        int number_of_effected_stock = port.stream().filter(asset->asset.getClass() == Stock.class).mapToInt(Asset::get_Quantity).sum()+ stock_obligation.stream().filter(obligation->obligation.getObligation_type().equals("Buy")).mapToInt(Stock::get_Quantity).sum();

        if ((dividend_per_stock > 0) && (number_of_effected_stock > 0)){
            //Do I have any stocks that a) I have to pay dividends on  b) receive dividends
            int number_of_stocks_receive_dividend = port.parallelStream().filter(asset->asset.getClass() == Stock.class).mapToInt(Asset::get_Quantity).sum(); //Number of stocks I get dividend payments for
            int number_of_stocks_pay_dividend = stock_obligation.stream().filter(obligation->obligation.getObligation_type().equals("Buy")).mapToInt(Stock::get_Quantity).sum(); //Number of stocks I must pay dividends to
            double net_dividends =dividend_per_stock*(number_of_stocks_receive_dividend-number_of_stocks_pay_dividend);

           if (net_dividends != 0){
               String order_id_1 = UUID.randomUUID().toString();
               Order order  = new Order(order_id_1,today,"Dividend","Pay","-","Pay dividends",1,net_dividends);
               add_order(order);
               //Add money
               add_money(net_dividends,today);
               //Log
               String signal_text =today+": Net dividends received: "+net_dividends;
               database.record_signal(signal_text);
           }

        }
    }


    public synchronized BigDecimal free_money(String current_date){
        double current_market_price = database.get_stock_value(current_date,"Open");
        double current_cash = get_cash_available(current_date);
        double stock_obligations_value = stock_obligation.stream().filter(obligation->obligation.getObligation_type().equals("Buy")).mapToDouble(obligation->obligation.get_Quantity()*current_market_price).sum();
        double option_exposure = getOptions().stream().mapToDouble(option->option.get_Asset_Value(current_date)).sum();

        double current_free_cash = current_cash-stock_obligations_value+option_exposure;

        return new BigDecimal(Math.max(0,current_free_cash));
    }

    public synchronized String toString(String current_date){
        double cash_amount = 0, stock_value = 0, option_value = 0;

        for (Asset asset : port){
            if (asset.getClass() == Cash.class){
                cash_amount += asset.get_Asset_Value(current_date);
            }
            else if (asset.getClass() == Stock.class){
                stock_value += asset.get_Asset_Value(current_date);
            }
            else if (asset.getClass() == Option.class){
                option_value += asset.get_Asset_Value(current_date);
            }
        }
       return  "cash:"+cash_amount/1000000+" stock: "+stock_value/1000000+ " option: "+option_value/1000000+" market: "+market_value(current_date).divide(new BigDecimal(1000000));
    }

    public BigDecimal neto_coverage_ratio(String current_date) {
        BigDecimal free_money = free_money(current_date);
        BigDecimal cash = new BigDecimal(get_cash_available(current_date));
        return free_money.divide(cash,RoundingMode.HALF_DOWN);
    }

    public void add_order(Order order) {
        order_log.add(order);
    }
    public Queue<Order> getOrder_log(){
        return order_log;
    }

    public void order_results_to_file(String result_file_name,String today){


        double value_of_order_log = order_log.stream().mapToDouble(Order::getProfit).sum();

        double portfolio_value_excluding_options = market_value(today).doubleValue()-getOptions().stream().mapToDouble(option->option.get_Asset_Value(today)).sum(); //-getOptions().stream().mapToDouble(option->option.get_Asset_Value(today)).sum();


        double test = 1000*1000+value_of_order_log;

        //Todo remove later
        System.out.println("Portfolio value according to the order log (M):"+test/1000000+ "portfolio value is(M): "+portfolio_value_excluding_options/1000000+" dif(M): "+(test-portfolio_value_excluding_options)/1000000);
        System.out.println(toString());



        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new File(result_file_name));
        } catch (FileNotFoundException e) {
            System.out.println("Order file not found");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("OrderID"+","+"Date"+","+"Order_type "+","+"Action"+","+"Indicator"+","+"Set_description"+","+"Number of Sets"+","+"Profit");
        sb.append('\n');
        pw.write(sb.toString());


        for (Order order : order_log){
            sb = new StringBuilder();
            //TODO
            sb.append(order.getOrderID()).append(",");
            sb.append(order.getDate()).append(",");
            sb.append(order.getOrder_Type()).append(",");
            sb.append(order.getAction()).append(",");
            sb.append(order.getIndicator()).append(",");
            sb.append(order.getSet_Description()).append(",");
            sb.append(order.getNumber_of_Sets()).append(",");
            sb.append(order.getProfit()).append(",");
            sb.append('\n');

            pw.write(sb.toString());

        }
        pw.close();

    }

    private double interest_payment(String current_date, List<Asset> options){
        double exercise_expense = options.stream().mapToDouble(o->o.exercise_value(current_date)).sum();

        double loan_amount = Math.min(get_cash_available(current_date)-exercise_expense,0);
        double interest_rate = database.get_treasury_value(current_date)/100.0; //annual

        return (loan_amount < 0) ? ((Math.abs(loan_amount) * Math.pow(1 + interest_rate/ 365, 3)) - Math.abs(loan_amount)) : 0;
    }
}
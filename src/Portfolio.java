import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class Portfolio {

    private List<Asset> port; //portfolio
    private Database database;
    private Queue<Stock> stock_obligation; // todo: it should be a priority que
    private HashMap<String,List<Cash>> cash_obligation;
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
        this.cash_obligation = new HashMap<>();
    }


    public synchronized BigDecimal market_value(String current_date){
        BigDecimal total_market_value = new BigDecimal(port.parallelStream().mapToDouble(p->p.get_Asset_Value(current_date)).sum(), MathContext.DECIMAL64);
        BigDecimal stock_obligation_exposure = new BigDecimal(stock_obligation.parallelStream().mapToDouble(p->p.get_Asset_Value(current_date)).sum(), MathContext.DECIMAL64);
        double cash_obligation = 0;


        //todo: implement lamda
        for (String date : getCash_obligation().keySet()){
            for (Cash cash : getCash_obligation().get(date)){
                cash_obligation += cash.get_Asset_Value(current_date);
            }
        }



        return total_market_value.subtract(stock_obligation_exposure).subtract(new BigDecimal(cash_obligation));
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
    public synchronized void add_stock_obligation(String obligation_date, String obligation_type, Stock stock){
        stock.setObligation(obligation_date,obligation_type);
        stock_obligation.add(stock);
    }

    public synchronized HashMap<String, List<Cash>> getCash_obligation() {
        return cash_obligation;
    }

    public synchronized double buy_stock(String order_id, String name, int quantity, double price, String current_date, String type, boolean implement_trading_fee){
        //type: used for order logging
        List<String> order_ids = order_log.stream().map(Order::getOrderID).collect(Collectors.toList());

        double money_to_be_taken = (quantity > 0 && price > 0) ? (quantity*price) : 0;
        if (money_to_be_taken == 0){ System.out.println("Error(buy stock): quantity or price negative"); return 0;}

        double trading_fee = (implement_trading_fee) ? stock_trading_fee(quantity,price) : 0;

        if (money_to_be_taken + trading_fee < get_cash_available(current_date)){
            money_to_be_taken = money_to_be_taken+ trading_fee;

            add_money(-money_to_be_taken,current_date);
            Stock stock = new Stock(order_id,name,database,quantity);
            add_asset(stock);

            if (type.equals("Initial") && order_ids.contains(order_id))
            {
                change_order_profit(order_id,"Stock",-money_to_be_taken);
            }
            else if (type.equals("Regular"))
            {
                change_order_profit(order_id,"Stock",-money_to_be_taken);
            }

            return trading_fee;
        }


        System.out.println("Not enough capital to buy stock (money needed = "+(money_to_be_taken+trading_fee)+" money at hand"+get_cash_available(current_date));
        return trading_fee;
    }
    public synchronized double sell_stock(String order_id,String name,int quantity, double price,String current_date,String type,boolean implement_trading_fee){
        //type: used for order logging
        double money_to_be_added = (quantity > 0 && price > 0) ? (quantity*price) : 0;


        if (money_to_be_added == 0){
            System.out.println("Error(selling stock): quantity or price negative");
            return 0;
        }
        double trading_fee = (implement_trading_fee) ? stock_trading_fee(quantity,price) : 0;
        money_to_be_added = money_to_be_added- trading_fee;

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

        return trading_fee;
    }

    public synchronized double buy_option(String order_id,String type,double strike_price,String signing_date,String exercise_date,double volatility,int number_of_stocks, boolean implement_trading_fee){
        double market_price = database.get_stock_value(signing_date,"Open");
        Option option = new Option(order_id,database,type,"Buy",strike_price,signing_date,exercise_date,volatility,market_price,number_of_stocks);

        double trading_fee = (implement_trading_fee) ? option_trading_fee(1,option.option_premium_BS(),"Buy",option.get_Strike()) : 0; //todo: always buying one option at a time


        //Do I have enough money to play the premium?
        if (get_cash_available(signing_date) + trading_fee >= option.option_premium_BS()*option.get_number_of_Stocks()){
            double income = -1*(option.option_premium_BS()*number_of_stocks+trading_fee);
            add_money(income,signing_date);

            add_asset(option);
            return trading_fee;
        }
        System.out.println("Error: not enough money for option premium payment. Money needed ("+(option.option_premium_BS()*option.get_number_of_Stocks())+") is bigger than the sum we have ("+get_cash_available(signing_date)+") ");
        return 0;

    }
    public synchronized double sell_option(String order_id,String type,double strike_price,String signing_date,String exercise_date,double volatility,int number_of_stocks,boolean implement_trading_fee){
        double market_price = database.get_stock_value(signing_date,"Open");
        Option option = new Option(order_id,database,type,"Sell",strike_price,signing_date,exercise_date,volatility,market_price,number_of_stocks);

        double trading_fee = (implement_trading_fee) ? option_trading_fee(1,option.option_premium_BS(),"Sell",option.get_Strike()) : 0; //todo: always buying one option at a time
        double income = option.option_premium_BS()*option.get_number_of_Stocks()-trading_fee;

        add_money(income,signing_date);

        add_asset(option);
        return trading_fee;

    }
    private void exercise_option(Option option, String today,boolean implement_trading_fee) {


        double current_market_price = database.get_stock_value(today, "Open");
        double income = 0, expense = 0, trading_fee = 0;

        //1. I have to exercise this option
        boolean i_must_exercise_this_option = option.get_Action().equals("Sell") && option.get_Asset_Value(today) >0;
        if (i_must_exercise_this_option) {
            //Call option
            expense = option.get_Strike() * option.get_number_of_Stocks();
            income = current_market_price * option.get_number_of_Stocks();

            //Put option
            if (option.get_Type().equals("Put")) {
                income = -income;
                expense = -expense;
            }
            }
        //2. I want to exercise this option
        boolean i_want_exercise_this_option = option.get_Action().equals("Buy") && option.get_Asset_Value(today) > 0;

        if (i_want_exercise_this_option) {
            //Call option
            expense = option.get_Strike() * option.get_number_of_Stocks();
            income = current_market_price * option.get_number_of_Stocks();

            //Put option
            if (option.get_Type().equals("Put")) {
                income = -income;
                expense = -expense;
            }
        }
        trading_fee += (implement_trading_fee) ? option_trading_fee(1,option.option_premium_BS(),"Buy",option.get_Strike()) : 0;
        trading_fee += (implement_trading_fee) ? option_trading_fee(1,option.option_premium_BS(),"Sell",current_market_price) : 0;


        double net_income = income-expense-trading_fee;
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


    public synchronized void exercise_obligations(String current_date,boolean implement_trading_fee){
        //1. Option obligation
        List<Asset> options_that_can_be_exercised = getOptions().parallelStream().filter(o-> o.can_be_exercised_today(current_date)).collect(toList());


        String end_date = LocalDate.parse(current_date).plusDays(90).toString(); //todo: implement general solution
        double interest_payment = (!options_that_can_be_exercised.isEmpty()) ? interest_payment(current_date,end_date,options_that_can_be_exercised,"Regular") : 0;

        options_that_can_be_exercised.forEach(o->exercise_option((Option)o,current_date,implement_trading_fee));
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
        stocks_that_will_be_sold_today.forEach(stock->sell_stock(stock.getOrder_id(),stock.get_Name(),stock.get_Quantity(),database.get_stock_value(current_date,"Open"),current_date,"Regular",implement_trading_fee));
        stocks_that_will_be_bought_today.forEach(stock->buy_stock(stock.getOrder_id(),stock.get_Name(),stock.get_Quantity(),database.get_stock_value(current_date,"Open"),current_date,"Regular",implement_trading_fee));
        //removing all obligations
        stock_obligation.removeAll(stocks_that_will_be_exercised_today);


        //3. Short selling intress payment
        if (cash_obligation.keySet().contains(current_date)){
            List<Cash> intress_obligations = cash_obligation.get(current_date);

            intress_obligations.forEach(payment-> {
                add_money(-payment.get_Asset_Value(current_date),current_date);
                change_order_profit(payment.getOblication_id(),"Stock",-payment.get_Asset_Value(current_date));
                });
            cash_obligation.remove(current_date);

        }


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

    public double interest_payment(String current_date, String end_date, List<Asset> options, String type){
        /*
        Type:
        2. Short-> used for short selling
        1. Regular -> used for in the money options
         */
        double time_difference;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date date1,date2;

        try {
            date1 = sdf.parse(current_date);
            date2 = sdf.parse(end_date);
            time_difference = Math.abs(date2.getTime()-date1.getTime())/(86400000);
            double exercise_expense = options.stream().mapToDouble(o->o.exercise_value(current_date)).sum();

            double loan_amount =  (type.equals("Regular")) ? Math.min(get_cash_available(current_date)-exercise_expense,0) : database.get_stock_value(current_date,"Open")*100;
            double interest_rate = database.get_treasury_value(current_date)/100.0; //annual

            double m = 360/time_difference;
            double var1 = 1/(interest_rate/m);
            double var2 = 1 - (1 / Math.pow(1 + interest_rate / m,m*time_difference/360));

            double annuity_payment = loan_amount / (var1*var2);

            return annuity_payment-loan_amount;



        } catch (ParseException e) {
            System.out.println("Error: Error calculating intrest payment. Current date and end date are not propery formated.");
            return 0;
        }

    }



    //Fees and oblications

    private double stock_trading_fee(int quantity,double price){
        //Using Interactive brokers fee structure: https://www.interactivebrokers.com/en/index.php?f=1590&p=stocks1

        if (quantity < 0){
            System.out.println("Error: Error calculation stock trading free. Quantity must be bigger than 0");
            return 0;
        }
        if (price < 0){
            System.out.println("Error: Error calculation stock trading free. Price must be bigger than 0");
            return 0;
        }
        double normal_fee = quantity*0.005;
        double min_fee = 1;
        double max_fee = price*quantity*1/100;


        return Math.min(Math.max(normal_fee,min_fee),max_fee);
    }
    private double option_trading_fee(int number_of_contacts,double premium_payment,String action,double strike){
        //Using Interactive brokers fee structure: https://www.interactivebrokers.com/en/index.php?f=commission&p=options1
        if (number_of_contacts < 0){
            System.out.println("Error: Error calculation option trading fee. number of option contracts can not be negative.");
            return 0;
        }
        //1. premium fee
        double premium_fee = (premium_payment >= 0.1) ? Math.max(number_of_contacts*0.7,1) : (premium_payment < 0.05) ? Math.max(number_of_contacts*0.25,1) : Math.max(number_of_contacts*0.5,1);
        // 2. NYSE fee
        double exchange_fee = (action.equals("Buy")) ? 1.1 : 0.5;
        //3. Regulatory Fee
        double regulatory_fee = 0;
        //4. Transaction fee
        double transaction_fee = (action.equals("Sell")) ? 0.0000231*number_of_contacts*strike + 0.002*number_of_contacts*100 : 0+ 0.002*number_of_contacts*100;
        //5. OCC Clearing fees
        double occ_fee = (number_of_contacts > 1100) ? 55 : 0.05*number_of_contacts;
        // Return the fee
        return premium_fee+exchange_fee+regulatory_fee+transaction_fee+occ_fee;
    }
    public void add_interest_obligation(String order_id, String obligation_date, double amount) {
        Cash cash = new Cash(amount);
        cash.setOblication_id(order_id);
        List<Cash> all_obligations_on_the_date = new ArrayList<>();
        all_obligations_on_the_date.add(cash);
        if (cash_obligation.containsKey(obligation_date)){
            List<Cash> previously_added_obligation_on_the_date = cash_obligation.get(obligation_date);
            all_obligations_on_the_date.addAll(previously_added_obligation_on_the_date);
        }
        cash_obligation.put(obligation_date,all_obligations_on_the_date);

    }
}
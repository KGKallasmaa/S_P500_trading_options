import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.*;

public class Reporter {

    private LinkedHashMap<String,BigDecimal> date_portfolio_value;
    private LinkedHashMap<String,BigDecimal> date_portfolio_value_without_options;
    private LinkedHashMap<String,BigDecimal> date_portfolio_cash_value;
    private LinkedHashMap<String,BigDecimal> date_neto_coverage;
    private LinkedHashMap<String,String> date_content;
    private Queue<Order> orderlog;


    Reporter(){
        this.date_portfolio_value = new LinkedHashMap<>();
        this.date_portfolio_value_without_options = new LinkedHashMap<>();
        this.date_portfolio_cash_value = new LinkedHashMap<>();
        this.date_neto_coverage = new LinkedHashMap<>();
        this.date_content = new LinkedHashMap<>();
        this.orderlog = new LinkedList<>();

    }
    public synchronized void report(Portfolio p, String current_date){
        //1. portfolio value
        date_portfolio_value.put(current_date,p.market_value(current_date));

        //2. portfolio value without options
        date_portfolio_value_without_options.put(current_date,p.value_without_options(current_date));

        //3. cash reserves
        date_portfolio_cash_value.put(current_date,new BigDecimal(p.get_cash_available(current_date)));

        //4. Neto coverage ratio
       date_neto_coverage.put(current_date,p.neto_coverage_ratio(current_date));

        //5. content of the portfolio
        date_content.put(current_date,p.toString(current_date));

        //6. empty order log <- implemented in the portfolio order_results_to_file
    }

    private void emptyOrderlog(Portfolio p){
        while (!p.getOrder_log().isEmpty()){
            orderlog.add(p.getOrder_log().poll());
        }
    }

    public void order_results_to_file(String result_file_name, Portfolio p){

        //6. empty order log
        emptyOrderlog(p);


        List<String> dates = new ArrayList<>();
        dates.addAll(date_portfolio_value.keySet());
        Collections.sort(dates);


        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new File(result_file_name));
        } catch (FileNotFoundException e) {
            System.out.println("Order file not found");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("OrderID"+","+"Date"+","+"Order_type "+","+"Action"+","+"Indicator"+","+"Set_description"+","+"Number of Sets"+","+"Profit");
        sb.append('\n');
        if (pw != null) {
            pw.write(sb.toString());
        }


        for (Order order : orderlog){
            sb = new StringBuilder();
            //TODO
            sb.append(order.getOrderID()).append(",");
            sb.append(order.getDate()).append(",");
            sb.append(order.getOrder_Type()).append(",");
            sb.append(order.getIndicator()).append(",");
            sb.append(order.getSet_Description()).append(",");
            sb.append(order.getNumber_of_Sets()).append(",");
            sb.append(order.getProfit()).append(",");
            sb.append('\n');

            pw.write(sb.toString());

        }
        pw.close();

    }

    public void results_to_file(String result_file_name){

        List<String> dates = new ArrayList<>();
        dates.addAll(date_portfolio_value.keySet());
        Collections.sort(dates);


        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new File(result_file_name));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Date"+","+"Portfolio_value"+","+"Portfolio_value_without_options"+","+"Portfolio_cash"+","+"Neto_coverage_ratio");
        sb.append('\n');
        if (pw != null) {
            pw.write(sb.toString());
        }

        for (String date : dates){
            sb = new StringBuilder();
            //TODO
            sb.append(date).append(",");
            sb.append(date_portfolio_value.get(date)).append(",");
            sb.append(date_portfolio_value_without_options.get(date)).append(",");
            sb.append(date_portfolio_cash_value.get(date)).append(",");
            sb.append(date_neto_coverage.get(date));
            sb.append('\n');

            pw.write(sb.toString());

        }
        pw.close();

    }


}

import java.util.Arrays;

public class Order {

    private String OrderID;
    private String Date;
    private String Order_Type;
    private String Action;
    private String Indicator;
    private String Set_Description;
    private int Number_of_Sets;
    private double Profit;

    Order(String orderID,String date, String order_type,String Action, String Indicator,String set_description, int number_of_sets, double profit){
        this.Date = date;
        this.OrderID = orderID;

        String [] order_types = {"Stock","Option","Dividend"};
        this.Order_Type = (Arrays.asList(order_types).contains(order_type)) ? order_type : "Empty";
        if (order_type.equals("Empty"))System.out.println("Order type can't be empty");

        String [] action_types = {"Buy","Sell","Pay","-"};
        this.Action = (Arrays.asList(action_types).contains(Action)) ? Action : "Empty";
        if (Action.equals("Empty"))System.out.println("Action can't be empty");

        String [] indicator_types = {"RSI","MAVG","PE","Unemployment"};
        this.Indicator = (Arrays.asList(indicator_types).contains(Indicator)) ? Indicator : "Empty";
        if (Indicator.equals("Empty"))System.out.println("Indicator can't be empty");

        this.Set_Description = set_description;
        this.Number_of_Sets = number_of_sets;
        this.Profit = profit;
    }

    public void addMoney(double amount){
        this.Profit = this.Profit + amount;
    }

    public String getOrderID() {
        return OrderID;
    }
    public String getDate() {
        return Date;
    }
    public double getProfit() {
        return Profit;
    }
    public int getNumber_of_Sets() {return Number_of_Sets;}
    public String getSet_Description() {return Set_Description;}
    public String getIndicator() {return Indicator;}
    public String getAction() {return Action;}
    public String getOrder_Type() {return Order_Type;}
}



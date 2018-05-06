public class Stock extends Asset {
    private String name;
    private String order_id;
    private Database database;
    private int quantity;
    private String obligation_date;
    private String obligation_type;

    Stock(String order_id,String name, Database db, int quantity) {
        this.order_id = order_id;
        this.name = name;
        this.database = db;
        this.quantity = quantity;
    }

    @Override
    public double get_Asset_Value(String current_date) {
        return database.get_stock_value(current_date, "Open") * quantity;
    }

    @Override
    public int get_Quantity() {
        return quantity;
    }

    @Override
    public String get_Name() {
        return name;
    }

    @Override
    public boolean can_be_exercised_today(String today) {
        return false;
    }

    @Override
    public double exercise_value(String current_date){
        System.out.println("Error: Stocks should not be exercised");
        return get_Asset_Value(current_date);
    }

    public void setObligation(String obligation_date, String obligation_type) {

        if (obligation_type.equals("Buy") || obligation_type.equals("Sell")) {
            this.obligation_date = obligation_date;
            this.obligation_type = obligation_type;
        } else {
            System.out.println("Error setting obligation type");
        }
    }

    public String getObligation_date() {
        return obligation_date;
    }

    public String getObligation_type() {
        return obligation_type;
    }
    public String getOrder_id(){
        return order_id;
    }




}
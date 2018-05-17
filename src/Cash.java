public class Cash extends Asset{

    private double cash_amount;
    private String oblication_id;
    private String order_id;

    Cash(String order_id, double amount){
        if (amount > 0){
            this.cash_amount = amount;
            this.order_id = order_id;
        }
    }

    public double get_Asset_Value(String current_date){
        return cash_amount;
    }
    @Override
    public int get_Quantity(){
        return 1;
    }



    public void setOblication_id(String obligation_id){
        this.oblication_id = obligation_id;
    }
    public String getOblication_id(){
        return oblication_id;
    }

    @Override
    public String getOrder_id() {
        return order_id;
    }

    @Override
    public double exercise_value(String current_date){
        System.out.println("Error: Cash should not be exercised");
        return get_Asset_Value(current_date);
    }


    @Override
    public String get_Name(){
        return "Cash";
    }

    @Override
    public boolean can_be_exercised_today(String today){
        return false;
    }

    }


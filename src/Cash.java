public class Cash extends Asset{

    private double cash_amount;

    Cash(double amount){
        if (amount > 0){
            this.cash_amount = amount;
        }
    }

    public double get_Asset_Value(String current_date){
        return cash_amount;
    }
    @Override
    public int get_Quantity(){
        return 1;
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


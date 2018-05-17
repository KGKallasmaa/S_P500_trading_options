public abstract class Asset {

    public abstract double get_Asset_Value(String current_date);

    public abstract double exercise_value(String current_date);

    public abstract int get_Quantity();

    public abstract String get_Name();

    public abstract boolean can_be_exercised_today(String today);

    public abstract String getOrder_id();
}

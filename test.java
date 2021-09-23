public class CallerApplication {
    public static ApplicationContext applicationContext;
    public static void main(String[] args) {
        applicationContext = SpringApplication.run(nicecall.CallerApplication.class, args);
    }
}
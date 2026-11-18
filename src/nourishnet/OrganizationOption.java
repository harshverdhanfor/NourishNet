package nourishnet;

public class OrganizationOption {
    private final int id;
    private final String name;

    public OrganizationOption(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() { return id; }
    public String getName() { return name; }
}

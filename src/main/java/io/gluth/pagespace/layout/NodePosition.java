package io.gluth.pagespace.layout;

public class NodePosition {

    private double x;
    private double y;
    private double z;

    NodePosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }

    void setX(double x) { this.x = x; }
    void setY(double y) { this.y = y; }
    void setZ(double z) { this.z = z; }

    public double distanceTo(NodePosition other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.max(Math.sqrt(dx * dx + dy * dy + dz * dz), 0.001);
    }

    @Override
    public String toString() { return "NodePosition[" + x + ", " + y + ", " + z + "]"; }
}

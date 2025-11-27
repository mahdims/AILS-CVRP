package Data;

public class Point
{
	public int name;
	public Double x;
	public Double y;
	public int demand;
	public int absenceCounter;
	
	public Point(int name) {
		super();
		this.name = name;
		this.absenceCounter = 0;
	}

	@Override
	public String toString() {
		return "Point [name=" + name + ", x=" + x + ", y=" + y + ", demand=" + demand + "]";
	}
	
}

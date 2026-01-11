package Perturbation;

public enum PerturbationType
{
	Sequential(0),
	Concentric(1),
	SISR(2),
	RouteRemoval(3),
	CriticalRemoval(4),
	RandomRemoval(5),
	PatternInjection(6);

	final int type;

	PerturbationType(int type)
	{
		this.type=type;
	}

}

package ca.polymtl.inf4410.tp2.shared;

import java.io.Serializable;
import java.util.ArrayList;

public class Tache implements Serializable {
	private ArrayList<Operation> Operations;
	private String assignedTo = null; // Nom serveur auquel a été assigné la tache. null si personne
	private String state;
	private Integer resultat = null;
	private int ID;
	private int parent_ID = 0;
	private int nbEssaieRealisation = 0;
	
	public Tache() {
		this.Operations = new ArrayList<Operation>();
		this.setToToDoState();
		this.ID = 0;
	}
	public Tache(int ID) {
		this.Operations = new ArrayList<Operation>();
		this.setToToDoState();
		this.ID = ID;
	}
	public Tache(ArrayList<Operation> Operations) {
		this.Operations = Operations;
		this.setToToDoState();
		this.ID = 0;
	}
	public Tache(ArrayList<Operation> Operations, int ID) {
		this.Operations = Operations;
		this.setToToDoState();
		this.ID = ID;
	}
	public void add(Operation Op) {
		this.Operations.add(Op);
	}
	public void compute() {
		this.setToInProgressState();
		this.resultat = 0;
		for (Operation operation : this.Operations) {
			try {
				this.resultat = (this.resultat + operation.compute() % 5000) % 5000;
			} catch (OperationUnknownException e) { e.printStackTrace(); }
		}
		this.setToFinishedState();
	}
	
	/**
	 * Fonction qui va séparer cette tâche en deux. <br>
	 * Cette tâche va perdre la moitié de ses opérations et l'autre moitié sera retourné dans une nouvelle tâche.
	 * @return Tache deuxième moitié de cette tâche qui vient d'être coupée en deux.
	 */
	public Tache cutInTwo() {
		// Si on a une tâche de moins de une opération on ne peut la couper en deux.
		if (this.Operations.size() <= 1) throw new ArrayIndexOutOfBoundsException("La taille de la tache ne peut être divisée (<=1)");
		int firsthalf = Math.round(this.Operations.size() / 2); // 1.5 sera 2 => première partie tjs plus grande que la seconde.
		ArrayList<Operation> Opperations2eTache = new ArrayList<Operation>();
		for (int i = firsthalf; i < this.Operations.size(); i++) {
			Opperations2eTache.add(this.Operations.get(firsthalf));
			this.Operations.remove(firsthalf);
		}
		Tache deuxiemeMoitie = new Tache(Opperations2eTache);
		return deuxiemeMoitie;
	}
	
	/** Petite fonction pour voir rapidement le contenu de la tache */
	public void show() {
		System.out.print(this.Operations.size()+" Opérations à effectuer : ");
		for (Operation operation : this.Operations) {
			System.out.print(operation.name+" "+operation.value+"; ");
		}
		System.out.println();
	}
	// State Getters & Setters
	public String getState(){ return this.state; }
	public void setToRefusedState(){ this.state = "Refused"; }
	public void setToNotDeliveredState(){ this.state = "NotDelivered"; }
	public void setToToDoState(){ this.state = "ToDo"; }
	public void setToFinishedState(){ this.state = "finished"; } 
	public void setToInProgressState(){ this.state = "inProgress"; }
	// AssignedTo Getters & Setters
	public String getAssignedTo() { return this.assignedTo; }
	public void setAssignedTo(String randomServerName) { this.assignedTo = randomServerName; }
	// resultat Getters & Setters
	public Integer getResultat() { return this.resultat; }
	public void setResultat(Integer resultat) { this.resultat = resultat; }
	/** getter pou le nombre d'opérations de la tache */
	public int getNbOperations(){return (Operations != null && !Operations.isEmpty())?Operations.size():0;}
	// ID Getters & Setters
	public int getID() {return ID;}
	public void setID(int iD) {ID = iD;}
	// parent_ID Getters & Setters
	public int getParent_ID() { return parent_ID;}
	public void setParent_ID(int parent_ID) { this.parent_ID = parent_ID;}
}

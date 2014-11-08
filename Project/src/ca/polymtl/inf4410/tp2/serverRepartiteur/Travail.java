package ca.polymtl.inf4410.tp2.serverRepartiteur;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import ca.polymtl.inf4410.tp2.shared.Operation;
import ca.polymtl.inf4410.tp2.shared.OperationUnknownException;
import ca.polymtl.inf4410.tp2.shared.Tache;

public class Travail {
	public static void main(String[] args) {
		Travail work = new Travail("/Documents/School/PolyMtl/Java/TP2_INF4410_1761581/Project/data_files/donnees-2684.txt",10);
		work.show();
		
		int result = 0;
		for (int i = 0; i < work.Taches.size(); i++) {
			Tache task = work.Taches.get(i);
			task.compute();
			work.addToComputedResult(task.getResultat());
			result = (result + task.getResultat() % 5000 ) % 5000;
			System.out.println(work.computedResult);
		}
		System.out.println("Résultat calculé : "+result);
		System.out.println("Résultat attendu : "+work.expectedResult);
	}
	
	public ArrayList<Operation> Operations;
	public ArrayList<Tache> Taches;
	public int expectedResult;
	private int tacheOperationsLoad = 10;
	public int computedResult = 0;
	
	public Travail(String path, int tacheOperationsLoad) {
		this.Operations = getOperationsFromFile(path);
		this.expectedResult = getExpectedResult(path);
		this.tacheOperationsLoad = tacheOperationsLoad;
		
		Taches = new ArrayList<Tache>();
		cutWorkInTasks(); // On va découper notre travail en taches
	}
	
	private void cutWorkInTasks() {
		ArrayList<Operation> curOperationsFlow = new ArrayList<Operation>();
		for (Operation operation : Operations) {
			curOperationsFlow.add(operation);
			if (curOperationsFlow.size() == this.tacheOperationsLoad) { // On passe à la tache suivante
				this.Taches.add(new Tache(curOperationsFlow,this.Taches.size()));
				curOperationsFlow = new ArrayList<Operation>();
			}
		}
		if (!curOperationsFlow.isEmpty()) {
			this.Taches.add(new Tache(curOperationsFlow,this.Taches.size()));
			curOperationsFlow = new ArrayList<Operation>();
		}
	}
	/**
	 * <p>Fonction qui va récupérer dans le nom du fichier le résultat attendu des opérations qu'il contient.<br>
	 * Exemple : donnees-4172.txt => le résultat attendu est 4172</p>
	 * @param path String chemin vers le fichier
	 * @return int résultat attendu par le calcul des opérations du fichier
	 */
	private static int getExpectedResult(String path){
		int retour = 0;
		File file = new File(path);
		String fileName = file.getName();
		if (fileName.matches("^donnees-[0-9]+[.]txt$")) {
			retour = Integer.valueOf(fileName.replaceAll("(donnees-|[.]txt)", ""));
		}
		return retour;
	}
	/**
	 * <p>Fonction pour récupérer les opérations d'un fichier donné<br>
	 * Chaque ligne du fichier texte d'entrée respecte le format: opération opérande<br>
	 * Exemple pour donnees-4172.txt :
	 * <ul><li>prime 208358</li><li>fib 32</li><li>fib 44</li></ul>
	 * </p>
	 * @param path String chemin vers le fichier
	 * @return ArrayList<Operation> liste des opérations que contient le fichier.
	 */
	private static ArrayList<Operation> getOperationsFromFile(String path) {
		ArrayList<Operation> Ops = new ArrayList<Operation>();
		
		// On récupère toutes les taches comprises dans notre fichier...
		BufferedReader br = null;
        String sCurrentLine = null;
        try {
            br = new BufferedReader(
            		new FileReader(path));
            while ((sCurrentLine = br.readLine()) != null) {
            	if (sCurrentLine.matches("^(fib|prime) [0-9]+$")) {
            		String[] explode = sCurrentLine.split(" ");
            		String name = explode[0];
            		int value = Integer.valueOf(explode[1]);
            		try {
            			Ops.add(new Operation(name, value));
					} catch (OperationUnknownException e) { e.printStackTrace(); }
				}
            }
        } catch (IOException e) { e.printStackTrace(); }
        finally{ // On ferme le fichier
        	try {
        		if (br != null) br.close();
        	} catch (IOException e){ e.printStackTrace(); }
    	}
        return Ops;
	}
	public void show() {
		System.out.println("Nous avons extrait "+this.Operations.size()+" Opérations à effectuer depuis le fichier passé.");
		System.out.println("Résultat attendu : "+this.expectedResult);
	}
	public void showTaches() {
		System.out.println("nous avons un travail de "+Operations.size()+" réparti en "+Taches.size()+" taches : ");
		for (Tache tache : Taches) {
			System.out.print("Tache #"+tache.getID()+" : ");
			tache.show();
		}
	}
	/**
	 * On va mettre à jour la tâche que nous a retourné le serveur de calcul
	 * @param task
	 */
	public synchronized void submitCompletedTask(Tache task) {
		this.Taches.set(task.getID(), task);
		this.addToComputedResult(task.getResultat());
	}
	/**
	 * Ajouter le résultat d'une tâche retournée au résultat final
	 * @param resultat
	 */
	public synchronized void addToComputedResult(Integer resultat) {
		this.computedResult = (this.computedResult + resultat % 5000 ) % 5000;
	}

	/** @return the tacheOperationsLoad */
	public int getTacheOperationsLoad() { return tacheOperationsLoad; }
	/** @param tacheOperationsLoad the tacheOperationsLoad to set */
	public void setTacheOperationsLoad(int tacheOperationsLoad) { this.tacheOperationsLoad = tacheOperationsLoad;}
}

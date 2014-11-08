package ca.polymtl.inf4410.tp2.serverRepartiteur;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ca.polymtl.inf4410.tp2.shared.ServerCalculInterface;
import ca.polymtl.inf4410.tp2.shared.Tache;

/**
 * @author Antoine Giraud #1761581
 *
 */
public class ServerRepartiteur {
	private enum functions {listServers, compute};
	public static void main(String[] args) {
		ServerRepartiteur serverRepartiteur = new ServerRepartiteur();
		if (args.length > 0) {
			functions f = functions.valueOf(args[0]);
			switch (f) {
				case listServers: serverRepartiteur.listServers(); break;
				case compute:     serverRepartiteur.compute(args[1]); break;
				default:          System.out.println("Mauvaise commande");
			}
		} else System.out.println("entrer une command comme argument");
	}

	private ServerCalculStubsManager stubManagr;
	private int tacheOperationsLoad = 10;
	private String[] RmiRegistryIpsToCheck = {"127.0.0.1"};

	/**
	 * Contructeur de la classe qui initialise diférentes choses :
	 * <ul><li>Le SecurityManager de RMI</li><li>La liste des serveurs disponibles</li><li>Lance le thread qui va vérifier que l'on a bien tous les serveurs de connecté toutes les 2 secondes.</li>
	 */
	public ServerRepartiteur() {
		
		this.readServerPropertiesFromFile();
		this.writeServerPropertiesInFile();
		
		this.stubManagr = new ServerCalculStubsManager(RmiRegistryIpsToCheck);
	}
	/**
	 * Affiche dans la console la liste des serveur disponibles dans le RMI.
	 */
	private void listServers() {
		stubManagr.listServers();
	}

	/**
	 * Commande disponible pour l'utilisateur qui va lui permettre d'effectuer le calcul d'un fichier d'opérations qu'il passe en paramètre.
	 * @param path
	 */
	private void compute(String path) {
		// On vérifie que le fichier passé en paramètre existe sinon on arrête le programme.
		if (!(new File(path).exists())) { System.out.println("Erreur, le fichier n'existe pas"); System.exit(0); }

		Travail work = new Travail(path, this.tacheOperationsLoad); // On a découpé notre projet en taches
		work.show();
		Hashtable<String, Hashtable<Integer, Integer>> countFailRequettesServer = new Hashtable<String, Hashtable<Integer, Integer>>();

		// On va effectuer les tâches...
		stubManagr.startServersMajWatch();
		
		ExecutorService execServ = Executors.newFixedThreadPool(10);
		ArrayList<Future<Tache>> futurSrvrRtrn = new ArrayList<Future<Tache>>();
		
		if (stubManagr.hasServers()) {
			// On va envoyer nos Taches !
			for (int i = 0; i < work.Taches.size(); i++) {
				String randomServerName = stubManagr.getRandomServerName();
				Tache task = work.Taches.get(i);
				task.setToInProgressState();
				task.setAssignedTo(randomServerName);
				work.Taches.set(i,task);

				futurSrvrRtrn.add(execServ.submit(new ComputeCallable(stubManagr.get(randomServerName), task)));
			}

			while (!futurSrvrRtrn.isEmpty()) {
				ArrayList<Future<Tache>> tamponFuturRetour = new ArrayList<Future<Tache>>();
				tamponFuturRetour.addAll(futurSrvrRtrn);
				for (Future<Tache> future : tamponFuturRetour) {
					if (future.isDone()) {
						futurSrvrRtrn.remove(future);
						try {
							Tache futureTask = future.get();

							if (futureTask.getResultat() == null) {
								if (futureTask.getState().equals("Refused")){
									incrementCountFailRequettesServer(countFailRequettesServer, futureTask.getAssignedTo(), futureTask.getNbOperations());
									System.out.println("# Tâche #"+futureTask.getID()+" refusée par "+futureTask.getAssignedTo());
								}else if (futureTask.getState().equals("NotDelivered"))
									System.out.println("# Tâche #"+futureTask.getID()+" n'a pas atteint "+futureTask.getAssignedTo());
								else
									System.out.println("# Tâche #"+futureTask.getID()+" non exécutée pour raison inconnue.");
								
								stubManagr.refreshServerList();
								if (!stubManagr.hasServers()) { // On n'a plus de serveurs connectés !!
									System.out.println("Aucun serveur disponible dans le RMI. Veuillez reconnecter un serveur de calcul au RMI pour continuer.");
									while (!stubManagr.hasServers()) {
										Thread.sleep(1500);
										stubManagr.refreshServerList();
									}
								}
								
								String randomServrName;
								if (stubManagr.hasServer(futureTask.getAssignedTo()) && futureTask.getState().equals("Refused") && 5 > countFailRequettesServer.get(futureTask.getAssignedTo()).get(futureTask.getNbOperations())) {
									randomServrName = futureTask.getAssignedTo();
								}else if(stubManagr.hasServer(futureTask.getAssignedTo()) && futureTask.getState().equals("Refused")){
									randomServrName = futureTask.getAssignedTo();
									Tache half = futureTask.cutInTwo();
									half.setToInProgressState();
									half.setAssignedTo(randomServrName);
									half.setParent_ID(futureTask.getID());
									half.setID(work.Taches.size());
									work.Taches.add(half);
									futurSrvrRtrn.add(execServ.submit(new ComputeCallable(stubManagr.get(randomServrName), half)));
									System.out.println("## T% refus trop important => division /2 de la tache pour "+randomServrName);
								}else
									randomServrName = stubManagr.getRandomServerName();
								futureTask.setToInProgressState();
								futureTask.setAssignedTo(randomServrName);
								futurSrvrRtrn.add(execServ.submit(new ComputeCallable(stubManagr.get(randomServrName), futureTask)));
							} else {
								System.out.println(futureTask.getAssignedTo()
										+ " réponds pour la tâche #"+futureTask.getID()+ " : " 
										+ futureTask.getResultat());
								work.submitCompletedTask(futureTask);
							}
						} catch (InterruptedException e) { e.printStackTrace(); }
						  catch (ExecutionException e) { e.printStackTrace(); }
					}

				}
			}
			System.out.println("Résultat calculé : " + work.computedResult);
			System.out.println("Résultat attendu : " + work.expectedResult);
			
			showCountFailRequettesServerArray(countFailRequettesServer);
		} else {
			System.out.println("Aucun serveur disponible dans le RMI. Veuillez lancer vos serveurs de calcul et recommencer.");
			System.exit(0);
		}
		execServ.shutdown();
		stubManagr.interruptServersMajWatch();
	}
	
	private void showCountFailRequettesServerArray(Hashtable<String, Hashtable<Integer, Integer>> countFailRequettesServer) {
		System.out.println("serverName:nbOpérations:count");
		if (!countFailRequettesServer.isEmpty()) { // On trouve les serveurs qui ont été coupés
			// On récupère les clés de notre Hashtable
			Set<String> set = countFailRequettesServer.keySet();
			String serverName;
			Iterator<String> serverNames = set.iterator();
			while (serverNames.hasNext()) {
				serverName = serverNames.next();
				Hashtable<Integer, Integer> cur = countFailRequettesServer.get(serverName);
				if (cur != null && !cur.isEmpty()) { // On trouve les serveurs qui ont été coupés
					// On récupère les clés de notre Hashtable
					Set<Integer> set2 = cur.keySet();
					Integer nbOp;
					Iterator<Integer> nbOps = set2.iterator();
					while (nbOps.hasNext()) {
						nbOp = nbOps.next();
						System.out.println(serverName+":"+nbOp+":"+countFailRequettesServer.get(serverName).get(nbOp));
					}
				}
			}
		}
	}
	
	private void incrementCountFailRequettesServer(Hashtable<String, Hashtable<Integer, Integer>> countFailRequettesServer,String serverName,Integer TaskSize) {
		if (countFailRequettesServer.containsKey(serverName)) {
			if (countFailRequettesServer.get(serverName).containsKey(TaskSize)) {
				if(countFailRequettesServer.get(serverName).get(TaskSize) == null){
					Hashtable<Integer, Integer> occ = new Hashtable<Integer, Integer>();
					occ.putAll(countFailRequettesServer.get(serverName));
					occ.put(TaskSize, 1);
					countFailRequettesServer.put(serverName, occ);
				}else{
					Hashtable<Integer, Integer> occ = new Hashtable<Integer, Integer>();
					occ.putAll(countFailRequettesServer.get(serverName));
					occ.put(TaskSize, countFailRequettesServer.get(serverName).get(TaskSize) +1);
					countFailRequettesServer.put(serverName, occ );
				}
			}else if(countFailRequettesServer.get(serverName) == null){
				Hashtable<Integer, Integer> occ = new Hashtable<Integer, Integer>();
				occ.put(TaskSize, 1);
				countFailRequettesServer.put(serverName, occ );
			}else{
				Hashtable<Integer, Integer> occ = new Hashtable<Integer, Integer>();
				occ.putAll(countFailRequettesServer.get(serverName));
				occ.put(TaskSize, 1);
				countFailRequettesServer.put(serverName, occ );
			}
			
			Hashtable<String, Hashtable<Integer, Integer>> temp = new Hashtable<String, Hashtable<Integer, Integer>>();
			
		}else{
			Hashtable<Integer, Integer> occ = new Hashtable<Integer, Integer>();
			occ.put(TaskSize, 1);
			countFailRequettesServer.put(serverName, occ );
		}
	}
	
	/**
	 * <p>Fonction pour lire les propriétés de notre serveur répartiteur<br>
	 * On utilise la classe {@link Properties} pour nous aider à gérer plus facilement notre fichier de conf...</p>
	 */
	private void readServerPropertiesFromFile() {
		Properties properties = new Properties();
		File f = new File("ServerRepartiteur.properties");
        if (!f.exists()) { try { 
        	f.createNewFile(); // Si le fichier n'existait pas on le créé
        	this.tacheOperationsLoad = 10;
        	this.RmiRegistryIpsToCheck = new String[]{"127.0.0.1"} ;
        } catch (IOException e1) { e1.printStackTrace();}}
        else{
	        FileInputStream fileInputStream = null;
			try { //Ouverture & lecture du fichier
				fileInputStream = new FileInputStream(f);
				properties.load(fileInputStream); // On charge les propriétés stockées dans notre fichier
				fileInputStream.close();
			} catch (IOException e) { e.printStackTrace(); }
	        if (fileInputStream != null) { // Si on a réussi à ouvrir le fichier
	        	this.tacheOperationsLoad = properties.getProperty("tacheOperationsLoad") == null ? 5 : Integer.valueOf(properties.getProperty("tacheOperationsLoad"));
	        	this.RmiRegistryIpsToCheck = properties.getProperty("RmiRegistryIpsToCheck") == null ? new String[]{"127.0.0.1"} : properties.getProperty("RmiRegistryIpsToCheck").split(";");
	        }else{
	        	this.tacheOperationsLoad = 10;
	        	this.RmiRegistryIpsToCheck = new String[]{"127.0.0.1"} ;
	        }
        }
	}
	/**
	 * <p>Fonction pour écrire les propriétés de notre serveur répartiteur<br>
	 * On utilise la classe {@link Properties} pour nous aider à gérer plus facilement notre fichier de conf...</p>
	 */
	private void writeServerPropertiesInFile() {
        Properties properties = new Properties();
        properties.setProperty("tacheOperationsLoad", Integer.toString(this.tacheOperationsLoad));
        properties.setProperty("RmiRegistryIpsToCheck", join(this.RmiRegistryIpsToCheck, ";"));
        //Store in the properties file
        File f = new File("ServerRepartiteur.properties");
        try {
			FileOutputStream fileOutputStream = new FileOutputStream(f);
			properties.store(fileOutputStream, null);
			fileOutputStream.close();
		} catch (FileNotFoundException e) { e.printStackTrace(); }
          catch (IOException e) { e.printStackTrace(); }
	}
	/**
	 * Petite fonction pour faire un implode tel en PHP
	 * @param StringArray String[] Array de String que l'on veut joindre en une chainde de charactère
	 * @param separator String séparateur qui permettra de différencier les valeurs de la chaine pour un explode prochain
	 * @return Tableau de String en une chaine String donc les valeurs sont séparées par le separator
	 */
	public String join(String[] StringArray, String separator) {
		String retour = "";
		for (String string : StringArray)
			retour += string + separator;
		return retour.substring(0, (retour.length() >= separator.length()) ? retour.length() - separator.length() : 0);
	}
	
}

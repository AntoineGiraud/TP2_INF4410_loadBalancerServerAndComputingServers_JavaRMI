package ca.polymtl.inf4410.tp2.serverRepartiteur;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

	private ServerCalculStubsManager stubsManager;
	private int tacheOperationsLoad = 10;
	private String[] RmiRegistryIpsToCheck = {"127.0.0.1"};

	/**
	 * Contructeur de la classe qui initialise diférentes choses :
	 * <ul><li>Le SecurityManager de RMI</li><li>La liste des serveurs disponibles</li><li>Lance le thread qui va vérifier que l'on a bien tous les serveurs de connecté toutes les 2 secondes.</li>
	 */
	public ServerRepartiteur() {
		
		this.readServerPropertiesFromFile();
		this.writeServerPropertiesInFile();
		
		this.stubsManager = new ServerCalculStubsManager(RmiRegistryIpsToCheck);
	}
	/**
	 * Affiche dans la console la liste des serveur disponibles dans le RMI.
	 */
	private void listServers() {
		stubsManager.listServers();
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

		// On va effectuer les tâches...
		stubsManager.startServersMajWatch();
		
		ExecutorService executorService = Executors.newFixedThreadPool(10);
		ArrayList<Future<Tache>> FutureRetoursDesServeurs = new ArrayList<Future<Tache>>();
		
		if (stubsManager.hasServers()) {
			// On va envoyer nos Taches !
			for (int i = 0; i < work.Taches.size(); i++) {
				String randomServerName = stubsManager.getRandomServerName();
				Tache task = work.Taches.get(i);
				task.setToInProgressState();
				task.setAssignedTo(randomServerName);
				work.Taches.set(i,task);

				FutureRetoursDesServeurs.add(executorService.submit(new ComputeCallable(stubsManager.get(randomServerName), task)));
			}

			while (!FutureRetoursDesServeurs.isEmpty()) {
				ArrayList<Future<Tache>> FutureRetoursDesServeurs2 = new ArrayList<Future<Tache>>();
				FutureRetoursDesServeurs2.addAll(FutureRetoursDesServeurs);
				for (Future<Tache> future : FutureRetoursDesServeurs2) {
					if (future.isDone()) {
						FutureRetoursDesServeurs.remove(future);
						try {
							Tache futureTask = future.get();

							if (futureTask.getResultat() == null) {
								if (futureTask.getState().equals("Refused"))
									System.out.println("Tâche #"+futureTask.getID()+" refusée par "+futureTask.getAssignedTo());
								else if (futureTask.getState().equals("NotDelivered"))
									System.out.println("Tâche #"+futureTask.getID()+" n'a pas atteint "+futureTask.getAssignedTo());
								else
									System.out.println("Tâche #"+futureTask.getID()+" non exécutée pour raison inconnue.");
								
								stubsManager.refreshServerList();
								if (!stubsManager.hasServers()) { // On n'a plus de serveurs connectés !!
									System.out.println("Aucun serveur disponible dans le RMI. Veuillez reconnecter un serveur de calcul au RMI pour continuer.");
									while (!stubsManager.hasServers()) {
										Thread.sleep(1500);
										stubsManager.refreshServerList();
									}
								}
								String randomServerName = stubsManager.getRandomServerName();
								futureTask.setToInProgressState();
								futureTask.setAssignedTo(randomServerName);
								FutureRetoursDesServeurs.add(executorService
										.submit(new ComputeCallable(
												stubsManager.get(randomServerName),
												futureTask)));
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
		} else {
			System.out.println("Aucun serveur disponible dans le RMI. Veuillez lancer vos serveurs de calcul et recommencer.");
			System.exit(0);
		}
		executorService.shutdown();
		stubsManager.interruptServersMajWatch();
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
	        
	        //Ouverture & lecture du fichier
			try {
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

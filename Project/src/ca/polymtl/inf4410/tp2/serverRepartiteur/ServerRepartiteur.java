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

import ca.polymtl.inf4410.tp2.shared.Tache;

/**
 * @author Antoine Giraud #1761581
 *
 */
public class ServerRepartiteur {
	private enum functions {listServers, secureCompute, nonSecureCompute};
	public static void main(String[] args) {
		ServerRepartiteur serverRepartiteur = new ServerRepartiteur();
		if (args.length > 0) {
			functions f = functions.valueOf(args[0]);
			switch (f) {
				case listServers:  serverRepartiteur.listServers(); break;
				case secureCompute:serverRepartiteur.secureCompute(args[1]); break;
				case nonSecureCompute:serverRepartiteur.nonSecureCompute(args[1]); break;
				default:           System.out.println("Mauvaise commande");
			}
		} else System.out.println("entrer une command comme argument");
	}

	private ServerCalculStubsManager stubManagr;
	private int tacheOperationsLoad = 10;
	private String[] RmiRegistryIpsToCheck = {"127.0.0.1"};
	private Hashtable<String, Hashtable<Integer, Integer>> countFailRequettesServer;
	private Hashtable<Integer, ArrayList<Tache>> tachesNonSecure;

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
	 * <p>Commande disponible pour l'utilisateur qui va lui permettre d'effectuer le calcul d'un fichier d'opérations qu'il passe en paramètre.</p>
	 * <p>secureCompute considère que les seveurs de calcul sont honêttes et ne font pas d'erreurs de calcul.</p>
	 * @param path String chemin vers le fichier contenant des opérations.
	 */
	private void secureCompute(String path) {
		// On vérifie que le fichier passé en paramètre existe sinon on arrête le programme.
		if (!(new File(path).exists())) { System.out.println("Erreur, le fichier n'existe pas"); System.exit(0); }

		Travail work = new Travail(path, this.tacheOperationsLoad); // On a découpé notre projet en taches
		work.show();
		countFailRequettesServer = new Hashtable<String, Hashtable<Integer, Integer>>();

		stubManagr.startServersMajWatch(); // On lance un thread qui fait une MAJ réguliaire de la liste des serveurs
		
		// On va effectuer les tâches...
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
				ArrayList<Future<Tache>> tamponFuturRetour = new ArrayList<Future<Tache>>(futurSrvrRtrn);
				for (Future<Tache> future : tamponFuturRetour) {
					if (future.isDone()) {
						futurSrvrRtrn.remove(future);
						try {
							Tache futureTask = future.get();

							if (futureTask.getResultat() == null) {
								if (futureTask.hasStateRefused()){
									incrementCountFailRequettesServer(futureTask.getAssignedTo(), futureTask.getNbOperations());
									System.out.println("# Tâche #"+futureTask.getID()+" refusée par "+futureTask.getAssignedTo());
								}else if (futureTask.hasStateNotDelivered()){
									stubManagr.checkServerAndMaybeRefreshList(futureTask.getAssignedTo());
									System.out.println("# Tâche #"+futureTask.getID()+" n'a pas atteint "+futureTask.getAssignedTo());
								}else{
									stubManagr.checkServerAndMaybeRefreshList(futureTask.getAssignedTo());
									System.out.println("# Tâche #"+futureTask.getID()+" non exécutée pour raison inconnue. Le serveur de calcul a surement crashé.");
								}
								stubManagr.checkServersAndMaybeWaitForMore();
								
								String randomServrName;
								if (stubManagr.hasServer(futureTask.getAssignedTo()) && futureTask.hasStateRefused() && 5 > countFailRequettesServer.get(futureTask.getAssignedTo()).get(futureTask.getNbOperations())) {
									randomServrName = futureTask.getAssignedTo();
								}else if(stubManagr.hasServer(futureTask.getAssignedTo()) && futureTask.hasStateRefused()){
									// On va couper la tâche en deux car on a eu trop de refus.
									randomServrName = stubManagr.getRandomServerName(); // On se permet d'envoyer la deuxième moitié à un autre serveur.
									Tache half = futureTask.cutInTwo();
									half.setToInProgressState();
									half.setAssignedTo(randomServrName); // On envera la seconde moitié à un serveur au hazard.
									half.setParent_ID(futureTask.getID());
									half.setID(work.Taches.size());
									work.Taches.add(half);
									futureTask.setChild_ID(half.getID());
									futurSrvrRtrn.add(execServ.submit(new ComputeCallable(stubManagr.get(randomServrName), half)));
									System.out.println("## T% refus trop important => division /2 de la tache pour "+randomServrName);
									randomServrName = futureTask.getAssignedTo(); // On envoie la première moitié au même serveur.
								}else
									randomServrName = stubManagr.getRandomServerName(); // Si le serveur initial n'est plus dispo on envoie la tâche à un autre
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
			
			showCountFailRequettesServerArray();
		} else {
			System.out.println("Aucun serveur disponible dans le RMI. Veuillez lancer vos serveurs de calcul et recommencer.");
			System.exit(0);
		}
		execServ.shutdown();
		stubManagr.interruptServersMajWatch();
	}
	
	/**
	 * <p>Commande disponible pour l'utilisateur qui va lui permettre d'effectuer le calcul d'un fichier d'opérations qu'il passe en paramètre.</p>
	 * <p>nonSecureCompute considère que les seveurs de calcul peuvent être malicieux et peuvent retourner un résultat érroné.<br>
	 * <p><strong>Doit-on gérer les défaillances de serveurs honnêtes en mode non-sécurisé?</strong><br>
	 * Oui, vous devez gérer tous les cas où il reste plus de 50% de calculateurs honnêtes.<br>
	 * Par exemple, si on démarre le système avec 3 calculateurs honnêtes et 1 calculateur malicieux, vous devez gérer la défaillance d'un des calculateurs honnêtes (il resterait alors 66% de calculateurs honnêtes).<br>
	 * Cependant, si on a initialement 2 calculateurs honnêtes et 1 calculateur malicieux (comme dans les tests de performance demandés), vous n'avez pas à gérer la panne d'un calculateur honnête (il resterait seulement 50% de calculateurs honnêtes).</p>
	 * @param path String chemin vers le fichier contenant des opérations.
	 */
	private void nonSecureCompute(String path) {
		// On vérifie que le fichier passé en paramètre existe sinon on arrête le programme.
		if (!(new File(path).exists())) { System.out.println("Erreur, le fichier n'existe pas"); System.exit(0); }

		Travail work = new Travail(path, this.tacheOperationsLoad); // On a découpé notre projet en taches
		work.show();
		tachesNonSecure = new Hashtable<Integer, ArrayList<Tache>>();
		countFailRequettesServer = new Hashtable<String, Hashtable<Integer, Integer>>();

		stubManagr.startServersMajWatch(); // On lance un thread qui fait une MAJ réguliaire de la liste des serveurs
		
		// On va effectuer les tâches...
		ExecutorService execServ = Executors.newFixedThreadPool(10);
		ArrayList<Future<Tache>> futurSrvrRtrn = new ArrayList<Future<Tache>>();
		
		if (stubManagr.getServersCount() <= 2) { // On n'a pas assez de serveurs connectés !!
			System.out.println("Nous n'avons pas assez de serveurs de calcul pour réaliser un calcul en mode non sécurisé.");
			System.out.println("Veuillez lancer au moins 3 serveurs de calcul. Il faut qu'il y ait plus de 50% de serveurs honêtes.");
			System.exit(0);
		}
		
		if (stubManagr.hasServers()) {
			// On va envoyer nos Taches !
			for (int i = 0; i < work.Taches.size(); i++) {
				ArrayList<Tache> nonSecureTasks = new ArrayList<Tache>();
				// On créé une première tâche pour un premier serveur
				String randomServerName = stubManagr.getRandomServerName();
				Tache task = new Tache(work.Taches.get(i));
				task.setToInProgressState();
				task.setAssignedTo(randomServerName);
				task.setNonSecureParent_ID(i);
				task.setID(nonSecureTasks.size());
				nonSecureTasks.add(task);
					futurSrvrRtrn.add(execServ.submit(new NonSecureComputeCallable(stubManagr.get(randomServerName), task)));
				// On créé une seconde tâche pour un second serveur
				randomServerName = stubManagr.getRandomServerName(randomServerName);
				task = new Tache(work.Taches.get(i));
				task.setToInProgressState();
				task.setAssignedTo(randomServerName);
				task.setNonSecureParent_ID(i);
				task.setID(nonSecureTasks.size());
				nonSecureTasks.add(task);
					futurSrvrRtrn.add(execServ.submit(new NonSecureComputeCallable(stubManagr.get(randomServerName), task)));
				// On ajoute l'ArrayList des tâches crées dans l'Hashtable à la clé de l'ID de la tâche courante (i)
				tachesNonSecure.put(i, nonSecureTasks);
			}
			
			// On traite le retour des tâches.
			while (!futurSrvrRtrn.isEmpty()) { // On boucle tant que l'on a pas traité toutes les réponses du serveur.
				ArrayList<Future<Tache>> tamponFuturRetour = new ArrayList<Future<Tache>>(futurSrvrRtrn);
				for (Future<Tache> future : tamponFuturRetour) {
					if (future.isDone()) {
						futurSrvrRtrn.remove(future);
						try {
							Tache futureTask = future.get();
							if (futureTask.getResultat() == null) { // La tâche n'a pas été calculée.
								// On vérifie que notre serveur est toujours présent en fonction du statut de la tâche retournée.
								boolean serverStillHere = true; // On suppose qu'il est tjs là par défaut.
								if (futureTask.hasStateRefused()){
									incrementCountFailRequettesServer(futureTask.getAssignedTo(), futureTask.getNbOperations());
									System.out.println("# Tâche #"+futureTask.getID()+" refusée par "+futureTask.getAssignedTo());
								}else if (futureTask.hasStateNotDelivered()){
									serverStillHere = stubManagr.checkServerAndMaybeRefreshList(futureTask.getAssignedTo());
									System.out.println("# Tâche #"+futureTask.getID()+" n'a pas atteint "+futureTask.getAssignedTo());
								}else{
									serverStillHere = stubManagr.checkServerAndMaybeRefreshList(futureTask.getAssignedTo());
									System.out.println("# Tâche #"+futureTask.getID()+" non exécutée pour raison inconnue. Le serveur de calcul a surement crashé.");
								}
								
								// On vérifie que l'on a tjs des serveurs connectés.
								if (stubManagr.checkHasServersAndMaybeWaitForMore(3)) {
									serverStillHere = stubManagr.checkServerAndMaybeRefreshList(futureTask.getAssignedTo());
								}
								
								// On agit en conséquence de l'état de la tâche et en fonction du serveur (présent/absent)
								ArrayList<Tache> tasks = new ArrayList<Tache>(tachesNonSecure.get(futureTask.getNonSecureParent_ID()));
								if (tasks.get(futureTask.getID()).hasStateCanceled()) {
									System.out.println("Les tâches du serveur "+futureTask.getAssignedTo()+" ont été anulées car il a été déconecté à un moment. la tache #"+futureTask.getID()+" ne sera plus considérée.");
									continue;// On passe à la tâche suivante.
								}
								String randomServrName;
								if (serverStillHere && futureTask.hasStateRefused() && 5 > countFailRequettesServer.get(futureTask.getAssignedTo()).get(futureTask.getNbOperations())) {
									randomServrName = futureTask.getAssignedTo();
								}else if(serverStillHere && futureTask.hasStateRefused()){
									// On va couper la tâche en deux car on a eu trop de refus.
									randomServrName = futureTask.getAssignedTo();
									Tache half = futureTask.cutInTwo();
									half.setToInProgressState();
									half.setAssignedTo(randomServrName); // On envera la seconde moitié à un serveur au hazard.
									half.setParent_ID(futureTask.getID());
									half.setNonSecureParent_ID(futureTask.getNonSecureParent_ID());
									half.setID(tasks.size());
									tasks.add(half);
									futureTask.setChild_ID(half.getID());
									futurSrvrRtrn.add(execServ.submit(new NonSecureComputeCallable(stubManagr.get(randomServrName), half)));
									System.out.println("## T% refus trop important => division /2 de la tache pour "+randomServrName);
								}else if(serverStillHere){ // Si le serveur existe toujours on réessaie de lui envoyer la tache.
									randomServrName = futureTask.getAssignedTo();
								}else{ // Serveur non joignable, on passe à un autre serveur... Et on doit recommencer de 0 ! ...
									// On marque comme annulé les tâches du serveur qui vient de se déconnecter...
									for (int i = 0; i < tasks.size(); i++) {
										Tache temp = tasks.get(i);
										temp.setToCanceledState();
										tasks.set(i, temp);
									}
									futureTask.setID(tasks.size());// Pour ne pas écraser les tâches du serveur précédent au cas où il revienne.
									// On s'assure que l'on se connecte bien à un nouveau serveur non présent avant !
									randomServrName = stubManagr.getRandomServerName(findServersInvolved(tachesNonSecure.get(futureTask.getNonSecureParent_ID())));
									// TODO Si jamais on retrouve un serveur qui avait été déconecté et reconecté et qu'il est choisi pour reprendre le travail, il va recommencer de 0. On pourrait code pour qu'il reprenne où il en était...
								}
								futureTask.setToInProgressState();
								futureTask.setAssignedTo(randomServrName);
								futurSrvrRtrn.add(execServ.submit(new NonSecureComputeCallable(stubManagr.get(randomServrName), futureTask)));
								tasks.add(futureTask.getID(),futureTask);
								tachesNonSecure.put(futureTask.getNonSecureParent_ID(),tasks);
							} else { // Le serveur a bien calculé notre tâche !
								ArrayList<Tache> tasks = new ArrayList<Tache>(tachesNonSecure.get(futureTask.getNonSecureParent_ID()));
								System.out.println(futureTask.getID()+" / "+tasks.size()+" // "+tachesNonSecure.get(futureTask.getNonSecureParent_ID()).size());
								tasks.add(futureTask.getID(),futureTask);
								
								System.out.println(futureTask.getAssignedTo()
										+ " réponds pour la tâche #"+futureTask.getID()+ " : " 
										+ futureTask.getResultat());
								if (computeServerResult(futureTask) == null) {
									if (!futureTask.hasStateFinished()) {throw new ArrayIndexOutOfBoundsException("Euhm Erreur, notre tâche arrivée ici devrait être marquée comme finie !");}
									// Si on a pas d'enfant, on n'est pas supposé avoir un résultat null ici.
									// En effet, le else dit que notre résultat n'est pas null.
									System.out.println("En attente des réponses des enfants.");
								}else{
									// On va compter les résultats présents et vérifier si l'on a assez de réponse pour trouver le résultat de la tâche.
									Hashtable<Integer, Integer> results = new Hashtable<Integer, Integer>();
									boolean egaliteResultatsServeurs = true;
									Integer maxCount = 0, maxResult = null;
									String serversDone = "", serversNotDone = "";
									for (Tache tache : tasks) {
										if (tache.getParent_ID() == null && !tache.hasStateCanceled()) {
											Integer computedResultThisServer = computeServerResult(futureTask);
											if (computedResultThisServer == null) {
												serversNotDone += tache.getAssignedTo();
												continue;
											}else{
												serversDone += tache.getAssignedTo();
												if (results.contains(computedResultThisServer)){
													int countUp = results.get(computedResultThisServer)+1;
													results.put(computedResultThisServer, countUp);
													if (maxCount < countUp) {
														maxCount = countUp;
														egaliteResultatsServeurs=false;
														maxResult=computedResultThisServer;
													}else if(maxCount == countUp){
														egaliteResultatsServeurs=true;
													}
												}else{
													results.put(computedResultThisServer, 1);
												}
											}
										}
									}// On a fini de compter les résultats
									if (!serversNotDone.isEmpty()) {
										// On a encore des tâches dont on doit recevoir des résultats.
									}else{
										//On va essayer de comparer notre résultat ..
										if (maxCount >= 2 && maxResult != null && egaliteResultatsServeurs == false) {
											Tache finalTask = new Tache(work.Taches.get(futureTask.getNonSecureParent_ID()));
											finalTask.setToFinishedState();
											finalTask.setResultat(maxResult);
											work.submitCompletedTask(finalTask);
											// tachesNonSecure.remove(futureTask.getNonSecureParent_ID());
										}else{ // On a besoin d'un avis de plus ...
											ArrayList<Tache> nonSecureTasks = new ArrayList<Tache>(tachesNonSecure.get(futureTask.getNonSecureParent_ID()));
											String randomServerName;
											ArrayList<String> serversInvolved = findServersInvolved(tachesNonSecure.get(futureTask.getNonSecureParent_ID()));
											try {
												randomServerName = stubManagr.getRandomServerName(serversInvolved);
											} catch (ArrayIndexOutOfBoundsException e) {
												e.printStackTrace();
												if (stubManagr.hasServers()) {
													System.out.println("il y a moins de 50% de serveurs honêtes.");
												}else{
													System.out.println("Il n'y a plus de serveurs connectés.");
												}
												System.out.println("Nous avons de plus besoin d'un serveur de plus pour déterminer le résultat.");
												System.out.println("Veuillez ajouter un nouveau serveur non malicieux.");
											}
												
											randomServerName = stubManagr.checkHasServersAndMaybeWaitForMore(serversInvolved);
											
											Tache task = new Tache(work.Taches.get(futureTask.getNonSecureParent_ID()));
											task.setToInProgressState();
											task.setAssignedTo(randomServerName);
											task.setNonSecureParent_ID(futureTask.getNonSecureParent_ID());
											task.setID(nonSecureTasks.size());
											nonSecureTasks.add(task);
												futurSrvrRtrn.add(execServ.submit(new NonSecureComputeCallable(stubManagr.get(randomServerName), task)));
										}
									}
								}
							}
						} catch (InterruptedException e) { e.printStackTrace(); }
						  catch (ExecutionException e) { e.printStackTrace(); }
					}
				}
			}
			System.out.println("Résultat calculé : " + work.computedResult);
			System.out.println("Résultat attendu : " + work.expectedResult);
			
			showCountFailRequettesServerArray();
		} else {
			System.out.println("Aucun serveur disponible dans le RMI. Veuillez lancer vos serveurs de calcul et recommencer.");
			System.exit(0);
		}
		execServ.shutdown();
		stubManagr.interruptServersMajWatch();
	}
	

	private Integer computeServerResult(Tache futureTask) {
		if (!futureTask.hasStateFinished()) {return null;} // ne devrait pas être le cas
		if (futureTask.getResultat() == null) {return null;}
		if (!(futureTask.getResultat() >= 0 && futureTask.getResultat() < 5000)) {futureTask.setResultat(futureTask.getResultat()%5000);}
		
		ArrayList<Tache> tasks = new ArrayList<Tache>(tachesNonSecure.get(futureTask.getNonSecureParent_ID()));
		if (futureTask.getChild_ID() == null) {
			return futureTask.getResultat();
		}else if(futureTask.getChild_ID() != null && tasks.contains(futureTask.getChild_ID()) && tasks.get(futureTask.getChild_ID()).getResultat() != null){
			Integer retourChild = computeServerResult(tasks.get(futureTask.getChild_ID()));
			if (retourChild == null) return null;
			else return (futureTask.getResultat() + retourChild)%5000;
		}else{
			return null;
		}
	}
	
	private ArrayList<String> findServersInvolved(ArrayList<Tache> nonSecureServersResults) {
		ArrayList<String> serversInvolved = new ArrayList<String>();
		for (Tache tache : nonSecureServersResults) {
			if (!tache.hasStateCanceled() && !serversInvolved.contains(tache.getAssignedTo())) {
				serversInvolved.add(tache.getAssignedTo());
			}
		}
		return serversInvolved;
	}
	
	private void showCountFailRequettesServerArray() {
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
	
	private void incrementCountFailRequettesServer(String serverName,Integer TaskSize) {
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

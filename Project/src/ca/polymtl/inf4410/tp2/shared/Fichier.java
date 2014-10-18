/**
 * 
 */
package ca.polymtl.inf4410.tp2.shared;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * <p>Cette classe nous est très utile car elle nous permet d'avoir une classe pour gérer nos fichiers et leurs propriétés.</p>
 * <p>On pourra même écrire sur le disque dur le contenu du fichier pour le conserver autrement qu'en mémoire.</p>
 * <p>la classe n'est pas faite pour gérer l'arborescence. <br>
 * Cependant, pour un souci de clarté dans l'organisation des fichiers sur notre serveur, on va pouvoir indiquer vers quel répertoire l'on souhaite enregistrer les fichiers.<br>
 * C'est au programme qui va utiliser les objets Fichiers de passer en argument un chemin vers le dossier dans lequel placer les fichiers.</p>
 * 
 * @author Antoine Giraud #1761581
 */
public class Fichier implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5848260167280425933L;
	private String nom;
	private Integer serverRepartiteurid;
	private byte[] contenu;
	
	public Fichier(String nom){
		this.nom = nom;
		this.serverRepartiteurid = 0;
		this.setFromFile("");
	}
	public Fichier(String nom,String path){
		this.nom = nom;
		this.serverRepartiteurid = 0;
		this.setFromFile(path);
	}
	
	/**
	 * <p>Va à la fois répondre si le fichier est vérouillé et le vérouiller si l'on peut le faire.</p>
	 * @param serverRepartiteurId int Identifiant du serverRepartiteur qui cherche à vérouiller le fichier
	 * @return boolean Est ce que le fichier est vérouillé ou non
	 */
	public boolean lockFile(int serverRepartiteurId){
		if(this.serverRepartiteurid == 0 || this.serverRepartiteurid == serverRepartiteurId){ // Le fichier n'appartient pas à qqn (ou à la limite on l'a déjà locké nous même), on peut le locker
			this.serverRepartiteurid = serverRepartiteurId;
			return true;
		}
		return false;
	}
	/**
	 * Fonction pour dévérouiller le fichier, on remet l'identifiant à 0 car il ne correspond à personne
	 */
	public void unlockFile(){
		this.serverRepartiteurid = 0;
	}
	
	public String getNomFichier(){
		return this.nom;
	}
	public int getServerRepartiteurId(){
		return this.serverRepartiteurid;
	}
	/**
	 * Retourne le contenu du fichier contenu dans la "mémoire vive". Ce n'est pas forcément ce qui est dans le fichier du même nom sur le disque dur.
	 * @return byte[] Tableau des bytes dont le fichier est composé
	 */
	public byte[] getFilecontent(){
		return this.contenu;
	}
	/**
	 * On assigne à notre fichier un nouveau contenu.
	 * @param contenu byte[] C'est le contenu en byte de notre nouveau fichier.
	 */
	public void setFilecontent(byte[] contenu){
		this.contenu = contenu;
	}
	/**
	 * <p>Nous allons lire le contenu du fichiers dont le path est nom. Si le fichier n'existe pas, on le créé.</p>
	 */
	public void setFromFile(String folder){
		Path path = Paths.get((folder.isEmpty()?"":folder+File.separator)+nom);
		try {
			if (!Files.exists(path)) {
				Files.createFile(path);
			}
			byte[] data = Files.readAllBytes(path);
			this.contenu = data;return;
		} catch (IOException e) { e.printStackTrace(); }
		this.contenu = null;
	}
	public void setFromFile(){
		this.setFromFile("");
	}
	/**
	 * <p>On écrit dans le fichier voulu le contenu du fichier que l'on a en mémoire.</p>
	 */
	public void writeInFile(String folder){
		Path path = Paths.get((folder.isEmpty()?"":folder+File.separator)+nom);
		try {
			if (!Files.exists(path)) {
				Files.createFile(path);
			}
			if (this.contenu != null) {
				Files.write(path, this.contenu);
			}
		} catch (IOException e) { e.printStackTrace(); }
	}
	public void writeInFile(){
		this.writeInFile("");
	}
}

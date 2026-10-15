package fr.cerelios.protocole;

import com.google.protobuf.ByteString;
import fr.cerelios.crypto.ChiffrementHybride;
import fr.cerelios.crypto.Elgamal;
import fr.cerelios.crypto.Preuves;
import fr.cerelios.protocole.messages.Bulletin;
import fr.cerelios.protocole.messages.Chiffre;
import fr.cerelios.protocole.messages.PartChiffree;
import fr.cerelios.protocole.messages.Polynome;
import fr.cerelios.protocole.messages.PreuveDechiffrement;
import fr.cerelios.protocole.messages.PreuveSchnorr;
import fr.cerelios.protocole.messages.PreuveZeroOuUn;
import java.math.BigInteger;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** Passage des types de {@code crypto} aux messages protobuf, et retour. */
public final class Conversions {

    private Conversions() {}

    public static ByteString octets(BigInteger n) {
        return ByteString.copyFrom(n.toByteArray());
    }

    /**
     * @throws IllegalArgumentException si le champ est vide : un entier absent n'est pas zéro
     */
    public static BigInteger entier(ByteString octets) {
        if (octets.isEmpty()) {
            throw new IllegalArgumentException("Entier attendu, champ vide");
        }
        return new BigInteger(octets.toByteArray());
    }

    public static Chiffre versProto(Elgamal.Chiffre c) {
        return Chiffre.newBuilder().setU(octets(c.u())).setV(octets(c.v())).build();
    }

    public static Elgamal.Chiffre depuisProto(Chiffre c) {
        return new Elgamal.Chiffre(entier(c.getU()), entier(c.getV()));
    }

    public static PreuveSchnorr versProto(Preuves.PreuveSchnorr p) {
        return PreuveSchnorr.newBuilder()
                .setDefi(octets(p.defi()))
                .setReponse(octets(p.reponse()))
                .build();
    }

    public static Preuves.PreuveSchnorr depuisProto(PreuveSchnorr p) {
        return new Preuves.PreuveSchnorr(entier(p.getDefi()), entier(p.getReponse()));
    }

    public static PreuveZeroOuUn versProto(Preuves.PreuveZeroOuUn p) {
        return PreuveZeroOuUn.newBuilder()
                .setDefi0(octets(p.defi0()))
                .setReponse0(octets(p.reponse0()))
                .setDefi1(octets(p.defi1()))
                .setReponse1(octets(p.reponse1()))
                .build();
    }

    public static Preuves.PreuveZeroOuUn depuisProto(PreuveZeroOuUn p) {
        return new Preuves.PreuveZeroOuUn(
                entier(p.getDefi0()),
                entier(p.getReponse0()),
                entier(p.getDefi1()),
                entier(p.getReponse1()));
    }

    public static PreuveDechiffrement versProto(Preuves.PreuveDechiffrement p) {
        return PreuveDechiffrement.newBuilder()
                .setDefi(octets(p.defi()))
                .setReponse(octets(p.reponse()))
                .build();
    }

    public static Preuves.PreuveDechiffrement depuisProto(PreuveDechiffrement p) {
        return new Preuves.PreuveDechiffrement(entier(p.getDefi()), entier(p.getReponse()));
    }

    public static PartChiffree versProto(int destinataire, ChiffrementHybride.Message m) {
        return PartChiffree.newBuilder()
                .setDestinataire(destinataire)
                .setR(octets(m.r()))
                .setNonce(ByteString.copyFrom(m.nonce()))
                .setDonnees(ByteString.copyFrom(m.donnees()))
                .build();
    }

    public static ChiffrementHybride.Message depuisProto(PartChiffree p) {
        return new ChiffrementHybride.Message(
                entier(p.getR()), p.getNonce().toByteArray(), p.getDonnees().toByteArray());
    }

    /** Engagements d'un polynôme. */
    public static List<BigInteger> entiers(List<ByteString> octets) {
        return octets.stream().map(Conversions::entier).toList();
    }

    /** Parts d'un polynôme, triées par destinataire. */
    public static SortedMap<Integer, ChiffrementHybride.Message> parts(Polynome polynome) {
        SortedMap<Integer, ChiffrementHybride.Message> parts = new TreeMap<>();
        for (PartChiffree p : polynome.getPartsList()) {
            if (parts.put(p.getDestinataire(), depuisProto(p)) != null) {
                throw new IllegalArgumentException(
                        "Deux parts pour le scrutateur " + p.getDestinataire());
            }
        }
        return parts;
    }

    public static Bulletin versProto(fr.cerelios.crypto.Bulletin b) {
        return Bulletin.newBuilder()
                .setCredential(octets(b.credential()))
                .setChiffre(versProto(b.chiffre()))
                .setPreuve(versProto(b.preuve()))
                .setSignature(versProto(b.signature()))
                .build();
    }

    public static fr.cerelios.crypto.Bulletin depuisProto(Bulletin b) {
        return new fr.cerelios.crypto.Bulletin(
                entier(b.getCredential()),
                depuisProto(b.getChiffre()),
                depuisProto(b.getPreuve()),
                depuisProto(b.getSignature()));
    }
}

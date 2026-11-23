package com.sanmiguelito;

/**
 * Punto de entrada del ejecutable. Se usa una clase distinta a la Application de JavaFX
 * para que el programa arranque desde el classpath empaquetado con jpackage.
 */
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}

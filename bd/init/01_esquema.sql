SET NAMES utf8mb4;

-- Esquema de la base de datos de monitores académicos.
-- MySQL ejecuta este archivo una sola vez, cuando el volumen de datos está vacío.

CREATE TABLE IF NOT EXISTS materias (
    id     INT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(20)  NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_materias_codigo (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- El código del monitor es la llave primaria: así la propia base de datos
-- impide los códigos duplicados (RF6).
CREATE TABLE IF NOT EXISTS monitores (
    codigo          VARCHAR(20)  NOT NULL,
    nombre_completo VARCHAR(120) NOT NULL,
    correo          VARCHAR(120) NOT NULL,
    materia_id      INT UNSIGNED NOT NULL,
    PRIMARY KEY (codigo),
    KEY idx_monitores_materia (materia_id),
    CONSTRAINT fk_monitores_materia FOREIGN KEY (materia_id)
        REFERENCES materias (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

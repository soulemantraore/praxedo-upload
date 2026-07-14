package com.praxedo.upload.domain.file.exceptions;

import com.praxedo.upload.domain.file.FileStatus;

/** Levee lorsqu'une transition de statut non autorisee est tentee sur un FileRecord. */
public class IllegalFileTransitionException extends RuntimeException {

    public IllegalFileTransitionException(FileStatus from, FileStatus to) {
        super("Transition interdite : " + from + " -> " + to);
    }
}

package se.fortnox.reactivewizard.db.transactions;

import rx.Observable;

import javax.inject.Inject;

import static java.util.Arrays.asList;
import static rx.Observable.empty;
import static rx.Observable.merge;

public class DaoTransactionsImpl implements DaoTransactions {
    @Inject
    public DaoTransactionsImpl() {
    }

    @Override
    public <T> void createTransaction(Observable<T>... daoCalls) {
        if (daoCalls == null || daoCalls.length == 0) {
            return;
        }

        createTransaction(asList(daoCalls));
    }

    @Override
    public <T> void createTransaction(Iterable<Observable<T>> daoCalls) {
        if (daoCalls == null || !daoCalls.iterator().hasNext()) {
            return;
        }

        for (Observable statement : daoCalls) {
            if (!(statement instanceof DaoObservable)) {
                String statementString  = statement == null ? "null" : statement.getClass().toString();
                String exceptionMessage = "All parameters to createTransaction needs to be observables coming from a Dao-class. Statement was %s.";
                throw new RuntimeException(String.format(exceptionMessage, statementString));
            }
        }

        Transaction transaction = new Transaction(daoCalls);
        for (Observable statement : daoCalls) {
            transaction.add((DaoObservable)statement);
        }
    }

    @Override
    public <T> Observable<Void> executeTransaction(Iterable<Observable<T>> daoCalls) {
        if (!daoCalls.iterator().hasNext()) {
            return empty();
        }
        createTransaction(daoCalls);
        return merge(daoCalls).last().flatMap(e -> empty());
    }

    @Override
    public <T> Observable<Void> executeTransaction(Observable<T>... daoCalls) {
        return executeTransaction(asList(daoCalls));
    }
}

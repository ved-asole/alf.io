/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.model.system.command;

import alfio.manager.payment.PaymentSpecification;
import alfio.model.transaction.PaymentProxy;

public class FinalizeReservation {
    private final PaymentSpecification paymentSpecification;
    private final PaymentProxy paymentProxy;
    private final boolean sendReservationConfirmationEmail;
    private final boolean sendTickets;
    private final String username;

    public FinalizeReservation(PaymentSpecification paymentSpecification, PaymentProxy paymentProxy, boolean sendReservationConfirmationEmail, boolean sendTickets, String username) {
        this.paymentSpecification = paymentSpecification;
        this.paymentProxy = paymentProxy;
        this.sendReservationConfirmationEmail = sendReservationConfirmationEmail;
        this.sendTickets = sendTickets;
        this.username = username;
    }

    public PaymentSpecification getPaymentSpecification() {
        return paymentSpecification;
    }

    public PaymentProxy getPaymentProxy() {
        return paymentProxy;
    }

    public boolean isSendReservationConfirmationEmail() {
        return sendReservationConfirmationEmail;
    }

    public boolean isSendTickets() {
        return sendTickets;
    }

    public String getUsername() {
        return username;
    }
}

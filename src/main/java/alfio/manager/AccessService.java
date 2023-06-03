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
package alfio.manager;

import alfio.config.authentication.support.APITokenAuthentication;
import alfio.manager.support.AccessDeniedException;
import alfio.model.PurchaseContext;
import alfio.model.user.Role;
import alfio.repository.*;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static alfio.config.authentication.support.AuthenticationConstants.SYSTEM_API_CLIENT;

/**
 * Centralized service for checking if a given Principal can
 *  - read a given resource with a specific id (example, get data of a user with a specific id)
 *  - update/delete a given resource with a specific id (example: update/delete an event)
 *  - do some specific action which affect a resource with a specific id (example: add a new event in a given organization)
 */
@Service
@Transactional(readOnly = true)
public class AccessService {

    private static final Logger log = LogManager.getLogger(AccessService.class);

    private final UserRepository userRepository;

    private final EventRepository eventRepository;

    private final AuthorityRepository authorityRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TicketReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final BillingDocumentRepository billingDocumentRepository;

    public AccessService(UserRepository userRepository,
                         AuthorityRepository authorityRepository,
                         UserOrganizationRepository userOrganizationRepository,
                         EventRepository eventRepository,
                         SubscriptionRepository subscriptionRepository,
                         TicketReservationRepository reservationRepository,
                         TicketRepository ticketRepository,
                         BillingDocumentRepository billingDocumentRepository) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.reservationRepository = reservationRepository;
        this.ticketRepository = ticketRepository;
        this.billingDocumentRepository = billingDocumentRepository;
    }

    public void checkUserAccess(Principal principal, int userId) {
        throw new AccessDeniedException();
    }

    public void checkOrganizationOwnership(Principal principal, int organizationId) {
        if (principal == null) {
            log.trace("No user present, we will allow it");
            return;
        }
        if (isSystemApiUser(principal)) {
            log.trace("Allowing ownership to Organization {} to System API Key", organizationId);
            return;
        }
        if (isOwnerOfOrganization(principal, organizationId)) {
            log.trace("Allowing ownership to Organization {} to user {}", organizationId, principal.getName());
            return;
        }
        log.warn("User {} don't have ownership to organizationId {}", principal.getName(), organizationId);
        throw new AccessDeniedException(); //"User " + principal.getName() + " don't have ownership to organizationId " + organizationId
    }

    public void checkEventOwnership(Principal principal, int eventId) {
        var orgId = eventRepository.findOrganizationIdByEventId(eventId);
        checkOrganizationOwnership(principal, orgId);
    }

    public void checkEventOwnership(Principal principal, String eventShortName) {
        var orgId = eventRepository.findOrganizationIdByShortName(eventShortName);
        checkOrganizationOwnership(principal, orgId);
    }

    private static boolean isSystemApiUser(Principal principal) {
        return principal instanceof APITokenAuthentication
            && ((APITokenAuthentication)principal).getAuthorities().stream()
            .allMatch(authority -> authority.getAuthority().equals("ROLE_" + SYSTEM_API_CLIENT));
    }

    private boolean isAdmin(Principal user) {
        return checkRole(user, Collections.singleton(Role.ADMIN));
    }

    private boolean isOwner(Principal principal) {
        return checkRole(principal, EnumSet.of(Role.ADMIN, Role.OWNER, Role.API_CONSUMER));
    }
    private boolean checkRole(Principal principal, Set<Role> expectedRoles) {
        var roleNames = expectedRoles.stream().map(Role::getRoleName).collect(Collectors.toSet());
        return authorityRepository.checkRole(principal.getName(), roleNames);
    }

    private boolean isOwnerOfOrganization(Principal principal, int organizationId) {
        return userRepository.findIdByUserName(principal.getName())
            .filter(userId ->
                    isAdmin(principal) ||
                    (isOwner(principal) && userOrganizationRepository.userIsInOrganization(userId, organizationId)))
            .isPresent();
    }


    public void checkReservationOwnership(Principal principal,
                                          PurchaseContext.PurchaseContextType purchaseContextType,
                                          String publicIdentifier,
                                          String reservationId) {
        if (purchaseContextType == PurchaseContext.PurchaseContextType.event) {
            checkOrganizationOwnershipForEvent(principal, publicIdentifier, reservationId);
        } else {
            var subscriptionDescriptor = subscriptionRepository.findDescriptorByReservationId(reservationId)
                .orElseThrow(AccessDeniedException::new);
            if (!subscriptionDescriptor.getPublicIdentifier().equals(publicIdentifier)) {
                throw new AccessDeniedException();
            }
        }
    }

    private void checkOrganizationOwnershipForEvent(Principal principal, String publicIdentifier, String reservationId) {
        var event = eventRepository.findOptionalEventAndOrganizationIdByShortName(publicIdentifier)
            .orElseThrow(AccessDeniedException::new);
        checkOrganizationOwnership(principal, event.getOrganizationId());
        var reservations = reservationRepository.getReservationIdAndEventId(List.of(reservationId));
        if (reservations.size() != 1 || reservations.get(0).getEventId() != event.getId()) {
            throw new AccessDeniedException();
        }
    }

    public void checkTicketOwnership(Principal principal,
                                     String publicIdentifier,
                                     String reservationId,
                                     int ticketId) {
        checkOrganizationOwnershipForEvent(principal, publicIdentifier, reservationId);
        var tickets = ticketRepository.findByIds(List.of(ticketId));
        if (tickets.size() != 1 || !tickets.get(0).getTicketsReservationId().equals(reservationId)) {
            throw new AccessDeniedException();
        }
    }

    public void checkBillingDocumentOwnership(Principal principal,
                                              PurchaseContext.PurchaseContextType purchaseContextType,
                                              String publicIdentifier,
                                              String reservationId,
                                              long billingDocumentId) {
        checkReservationOwnership(principal, purchaseContextType, publicIdentifier, reservationId);
        if (!Boolean.TRUE.equals(billingDocumentRepository.checkBillingDocumentExistsForReservation(billingDocumentId, reservationId))) {
            throw new AccessDeniedException();
        }
    }
}
package org.fao.geonet.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Tuple;
import javax.persistence.criteria.*;

import com.google.common.base.Optional;
import org.fao.geonet.domain.*;
import org.hibernate.ejb.criteria.OrderImpl;
import org.springframework.data.domain.Sort;

/**
 * Implementation for all {@link User} queries that cannot be automatically generated by Spring-data.
 * 
 * @author Jesse
 */
public class UserRepositoryImpl implements UserRepositoryCustom {

    @PersistenceContext
    private EntityManager _entityManager;

    @Override
    public User findOne(String userId) {
        return _entityManager.find(User.class, Integer.valueOf(userId));
    }
    @Override
    public List<User> findAllByEmail(String email) {

        // The following code uses the JPA Criteria API to build a query
        // that is essentially:
        //      Select * from Users where email in (SELECT
        CriteriaBuilder cb = _entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);

        query.where(cb.isMember(email, root.get(User_.emailAddresses)));
        return _entityManager.createQuery(query).getResultList();
    }

    @Override
    @Nonnull
    public List<Pair<Integer,User>> findAllByGroupOwnerNameAndProfile(@Nonnull Collection<Integer> metadataIds,
                                                               @Nullable Profile profile, @Nullable Sort sort) {
        CriteriaBuilder cb = _entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createQuery(Tuple.class);

        Root<User> userRoot = query.from(User.class);
        Root<Metadata> metadataRoot = query.from(Metadata.class);
        Root<UserGroup> userGroupRoot = query.from(UserGroup.class);

        query.multiselect(metadataRoot.get(Metadata_.id), userRoot);

        Predicate metadataPredicate = metadataRoot.get(Metadata_.id).in(metadataIds);
        Predicate ownerPredicate = cb.equal(metadataRoot.get(Metadata_.sourceInfo).get(MetadataSourceInfo_.groupOwner),
                userGroupRoot.get(UserGroup_.id).get(UserGroupId_.groupId));
        Predicate userToGroupPredicate = cb.equal(userGroupRoot.get(UserGroup_.id).get(UserGroupId_.userId), userRoot.get(User_.id));

        Predicate basePredicate = cb.and(metadataPredicate, ownerPredicate, userToGroupPredicate);
        if (profile != null) {
            Expression<Boolean> profilePredicate = cb.equal(userGroupRoot.get(UserGroup_.profile), profile);
            query.where(cb.and(basePredicate, profilePredicate));
        } else {
            query.where(basePredicate);
        }
        if (sort != null) {
            List<Order> orders = SortUtils.sortToJpaOrders(cb, sort, userGroupRoot, metadataRoot, userRoot);
            query.orderBy(orders);
        }

        List<Pair<Integer, User>> results = new ArrayList<Pair<Integer, User>>();

        for (Tuple result : _entityManager.createQuery(query).getResultList()) {
            Integer mdId = (Integer) result.get(0);
            User user = (User) result.get(1);
            results.add(Pair.read(mdId, user));
        }
        return results;
    }

}

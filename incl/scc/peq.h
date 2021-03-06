/*******************************************************************************
 * Copyright 2018 MINRES Technologies GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

#ifndef _SCC_PEQ_H_
#define _SCC_PEQ_H_

#include <boost/optional.hpp>
#include <map>
#include <systemc>
#include <type_traits>

namespace scc {
/**
 * a simple priority event queue with a copy of the original value
 */
template <class TYPE> struct peq : public sc_core::sc_object {

    static_assert(std::is_copy_constructible<TYPE>::value, "TYPE needs to be copy-constructible");

    using pair_type = std::pair<const sc_core::sc_time, TYPE>;

    peq()
    : sc_core::sc_object(sc_core::sc_gen_unique_name("peq")) {}

    explicit peq(const char* name)
    : sc_core::sc_object(name) {}

    void notify(const TYPE& trans, const sc_core::sc_time& t) {
        m_scheduled_events.insert(pair_type(t + sc_core::sc_time_stamp(), trans));
        m_event.notify(t);
    }

    void notify(const TYPE& trans) {
        m_scheduled_events.insert(pair_type(sc_core::sc_time_stamp(), trans));
        m_event.notify(); // immediate notification
    }

    boost::optional<TYPE> get_next() {
        if(m_scheduled_events.empty())
            return boost::none;
        ;
        sc_core::sc_time now = sc_core::sc_time_stamp();
        if(m_scheduled_events.begin()->first <= now) {
            auto trans = m_scheduled_events.begin()->second;
            m_scheduled_events.erase(m_scheduled_events.begin());
            return trans;
        }
        m_event.notify(m_scheduled_events.begin()->first - now);
        return boost::none;
        ;
    }

    sc_core::sc_event& event() { return m_event; }

    // Cancel all events from the event queue
    void cancel_all() {
        m_scheduled_events.clear();
        m_event.cancel();
    }

private:
    std::multimap<const sc_core::sc_time, TYPE> m_scheduled_events;
    sc_core::sc_event m_event;
};
} // namespace scc

#endif /* _SCC_PEQ_H_ */

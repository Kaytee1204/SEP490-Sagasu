import { createContext } from 'react';

const ChatContext = createContext({ revision: 0, online: false, unread: 0, refresh: () => {} });

export default ChatContext;

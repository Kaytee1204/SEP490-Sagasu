import { useNavigate } from 'react-router-dom';
import CounterIntakeContent from '../components/CounterIntakeContent';
import '../Office.css';
export default function CounterIntakePage(){const navigate=useNavigate();return <CounterIntakeContent onClose={()=>navigate('/approver/transfers')} onDone={()=>{}}/>;}
